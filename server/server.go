package main

import (
	"encoding/base64"
	"encoding/json"
	"flag"
	"io/ioutil"
	"log"

	"github.com/aliafshar/gcm"
	"github.com/golang/protobuf/proto"

	pb "./proto"
)

const (
	newClientTopic = "/topics/newclient"
)

var (
	apiKey   = flag.String("apiKey", "your_api_key", "The api key authorized to send messages")
	senderId = flag.String("senderId", "your_sender_id", "The sender id to identify the app on the GCM server")
	testData = flag.String("testData", "", "Optional: test data to load (clients)")
)

// The friendly ping server
type fpServer struct {
	apiKey   string
	senderId string
	clients  map[string]*pb.Client
}

// Callback for gcmd listen: check action and dispatch server method
func (s *fpServer) onMessage(from string, m gcm.Message) error {
	fpMessage, err := s.unpackMessageProto(m)
	if err != nil {
		return err
	}
	switch fpMessage.GetAction() {
	case pb.Action_REGISTER_NEW_CLIENT:
		return s.registerNewClient(fpMessage.GetRncPayload())
	case pb.Action_PING_CLIENT:
		return s.pingClient(fpMessage.GetPcPayload())
	}
	return nil
}

// Send the list of connected clients to a client
func (s *fpServer) sendClientList(to string, clientList []*pb.Client) error {
	payload := &pb.SendClientList{Clients: clientList}
	action := pb.Action(pb.Action_SEND_CLIENT_LIST).Enum()
	messageProto := &pb.FriendlyPingMessage{Action: action, SclPayload: payload}
	message, err := s.protoToBase64String(messageProto)
	if err != nil {
		return err
	}
	return gcm.Send(s.apiKey, to, gcm.Message{"base64": message})
}

// Broadcast a new client sending a message to the new client topic
func (s *fpServer) broadcastNewClient(c *pb.Client) error {
	messageProto := &pb.BroadcastNewClient{Client: c}
	message, err := s.protoToBase64String(messageProto)
	if err != nil {
		return err
	}
	return gcm.Send(s.apiKey, newClientTopic, gcm.Message{"base64": message})
}

// Add new client to registered clients, send list of registered clients to new client
// broadcast the new client to new client topic
func (s *fpServer) registerNewClient(rncPayload *pb.RegisterNewClient) error {
	c := rncPayload.GetClient()
	s.clients[c.GetRegistrationToken()] = c
	err := s.broadcastNewClient(c)
	if err != nil {
		// TODO(silvano): sshould panic and retry?
		log.Printf("Failed broadcasting the new client: %v", err)
	}
	err = s.sendClientList(c.GetRegistrationToken(), s.getClientList())
	if err != nil {
		// TODO(silvano): should panic and retry?
		log.Printf("Failed broadcasting the new client: %v", err)

	}
	return err
}

// Send a ping
func (s *fpServer) pingClient(pcPayload *pb.PingClient) error {
	// TODO(silvano): The downside of sending the payload as base64 encoded protobuf is that we won't be able to
	// take advantage of APNS notifications, because the GCM server expects a certain format for the payload.
	// (If I understood correctly, on Android this is not a problem because the app always manages the reception
	// of the notification.
	// One solution could be to flag if a client is an iOS device, and if that's the case, transform the payload.
	message, err := s.protoToBase64String(pcPayload)
	if err != nil {
		return err
	}
	return gcm.Send(s.apiKey, pcPayload.GetTo().GetRegistrationToken(),
		gcm.Message{"base64": message})
}

// Transform the map of connected clients to an array of clients
func (s *fpServer) getClientList() []*pb.Client {
	i := 0
	cl := make([]*pb.Client, len(s.clients))
	for k := range s.clients {
		cl[i] = s.clients[k]
		i++
	}
	return cl
}

// Extract a Friendly Ping message from a gcmd message
func (s *fpServer) unpackMessageProto(message gcm.Message) (*pb.FriendlyPingMessage, error) {
	data, err := base64.StdEncoding.DecodeString(message["base64"].(string))
	if err != nil {
		log.Printf("Failed to decode message payload: %v", err)
		return nil, err
	}
	fpMessage := new(pb.FriendlyPingMessage)
	err = proto.Unmarshal(data, fpMessage)
	if err != nil {
		log.Printf("Unmarshaling error: ", err)
		return nil, err
	}
	return fpMessage, nil
}

// Rocket science
func (s *fpServer) protoToBase64String(message proto.Message) (string, error) {
	data, err := proto.Marshal(message)
	if err != nil {
		return "", err
	}
	return base64.StdEncoding.EncodeToString(data), nil
}

// Load test data for the server
func (s *fpServer) loadTestData() {
	file, err := ioutil.ReadFile(*testData)
	if err != nil {
		log.Fatalf("Failed to read test data file: %v", err)
	}
	if err := json.Unmarshal(file, &s.clients); err != nil {
		log.Fatalf("Failed to unmarshal test data: %v", err)
	}
}

// Factory method for the fpServer
func newServer(apiKey, senderId string) *fpServer {
	s := &fpServer{apiKey: apiKey, senderId: senderId, clients: make(map[string]*pb.Client)} 
	if *testData != "" {
		s.loadTestData()
	}
	return s
}

// Booyakasha
func main() {
	flag.Parse()
	fpServer := newServer(*apiKey, *senderId)
	err := gcm.Listen(*senderId, *apiKey, fpServer.onMessage, nil)
	if err != nil {
		panic(err)
	}
}
