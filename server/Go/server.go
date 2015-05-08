package main

import (
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io/ioutil"
	"log"

	"github.com/aliafshar/gcm"
)

const (
	newClientTopic     = "/topics/newclient"
	actionKey          = "action"
	senderKey          = "sender"
	registerNewClient  = "register_new_client"
	broadcastNewClient = "broadcast_new_client"
	sendClientList     = "send_client_list"
	pingClient         = "ping_client"
	pingTitle          = "Friendly Ping!"
)

var (
	apiKey   = flag.String("apiKey", "your_api_key", "The api key authorized to send messages")
	senderId = flag.String("senderId", "your_sender_id", "The sender id to identify the app on the GCM server")
	testData = flag.String("testData", "", "Optional: test data to load (clients)")
)

// Standard format of the ping messages
func createPingMessage(name string) string {
	return fmt.Sprintf("%s is pinging you!", name)
}

// The friendly ping server
type fpServer struct {
	apiKey   string
	senderId string
	clients  map[string]*Client
}

// A friendly ping client
type Client struct {
	Name              string `json:"name,omitempty"`
	RegistrationToken string `json:"registration_token,omitempty"`
	ProfilePictureUrl string `json:"profile_picture_url,omitempty"`
}

// Callback for gcmd listen: check action and dispatch server method
func (s *fpServer) onMessage(from string, d gcm.Data) error {
	switch d[actionKey] {
	case registerNewClient:
		return s.registerNewClient(d)
	case pingClient:
		return s.pingClient(d)
	}
	return nil
}

// Add new client to registered clients, send list of registered clients to new client
// broadcast the new client to new client topic
func (s *fpServer) registerNewClient(d gcm.Data) error {
	name, ok := d["name"].(string)
	if !ok {
		return errors.New("Error decoding name for new client")
	}
	registrationToken, ok := d["registration_token"].(string)
	if !ok {
		return errors.New("Error decoding registration token for new client")
	}
	profilePictureUrl, ok := d["profile_picture_url"].(string)
	if !ok {
		return errors.New("Error decoding profile picture for new client")
	}
	client := &Client{name, registrationToken, profilePictureUrl}
	s.clients[client.RegistrationToken] = client
	err := s.broadcastNewClient(*client)
	if err != nil {
		// TODO(silvano): sshould panic and retry?
		log.Printf("Failed broadcasting the new client: %v", err)
	}
	err = s.sendClientList(*client)
	if err != nil {
		// TODO(silvano): should panic and retry?
		log.Printf("Failed sending client list: %v", err)

	}
	return err
}

// Broadcast a new client sending a message to the new client topic
func (s *fpServer) broadcastNewClient(c Client) error {
	return gcm.Send(s.apiKey, gcm.Message{To: newClientTopic, Data: gcm.Data{actionKey: broadcastNewClient, "client": c}})
}

// Send the list of clients to the newly registered client
func (s *fpServer) sendClientList(c Client) error {
	return gcm.Send(s.apiKey, gcm.Message{To: c.RegistrationToken, Data: gcm.Data{actionKey: sendClientList, "clients": s.getClientList()}})
}

func (s *fpServer) pingClient(d gcm.Data) error {
	// This is a test ping, so we retrieve the information of the server from the client list
	client := s.clients[s.getServerGcmAddress()]
	// TODO(silvano): add note about the effect of sending the notification (I'm not clear about the android side)
	notification := &gcm.Notification{Text: createPingMessage(client.Name), Title: "Friendly Ping!"}
	senderVal, ok := d[senderKey]
	if !ok {
		return errors.New("Error parsing sender from ping message")
	}
	sender, ok := senderVal.(string)
	if !ok {
		return errors.New("Error parsing sender from ping message")
	}
	return gcm.Send(s.apiKey, gcm.Message{To: sender, Data: gcm.Data{actionKey: pingClient, senderKey: s.getServerGcmAddress()}, Notification: *notification})
}

// Transform the map of connected clients to an array of clients
func (s *fpServer) getClientList() []*Client {
	i := 0
	cl := make([]*Client, len(s.clients))
	for k := range s.clients {
		cl[i] = s.clients[k]
		i++
	}
	return cl
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

func (s *fpServer) getServerGcmAddress() string {
	return fmt.Sprintf("%s@gcm.googleapis.com", s.senderId)
}

// Factory method for the fpServer
func newServer(apiKey, senderId string) *fpServer {
	s := &fpServer{apiKey: apiKey, senderId: senderId, clients: make(map[string]*Client)}
	if *testData != "" {
		s.loadTestData()
	}
	serverAddress := s.getServerGcmAddress()
	s.clients[serverAddress] = &Client{"Larry", serverAddress, "https://lh3.googleusercontent.com/-Y86IN-vEObo/AAAAAAAAAAI/AAAAAAADO1I/QzjOGHq5kNQ/photo.jpg?sz=50"}
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
