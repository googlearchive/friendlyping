// Copyright Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package main

import (
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io/ioutil"
	"log"
	"sync"

	"github.com/google/go-gcm"
)

const (
	newClientTopic     = "/topics/newclient"
	actionKey          = "action"
	senderKey          = "sender"
	toKey              = "to"
	registerNewClient  = "register_new_client"
	broadcastNewClient = "broadcast_new_client"
	sendClientList     = "send_client_list"
	pingClient         = "ping_client"
	pingTitle          = "Friendly Ping!"
	androidIcon        = "mipmap/ic_launcher"
	pingReceived       = "ping_received"
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
	clients  Clients
}

type Clients struct {
	sync.RWMutex
	c map[string]*Client
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
		_, err := s.pingClient(d)
		if err != nil {
			log.Printf("Failed pinging client: %v", err)
		}
		return err
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
	s.clients.Lock()
	s.clients.c[client.RegistrationToken] = client
	s.clients.Unlock()
	_, err := s.broadcastNewClient(*client)
	if err != nil {
		log.Printf("Failed broadcasting the new client: %v", err)
	}
	_, err = s.sendClientList(*client)
	if err != nil {
		log.Printf("Failed sending client list: %v", err)

	}
	return err
}

// Broadcast a new client sending a message to the new client topic
func (s *fpServer) broadcastNewClient(c Client) (*gcm.HttpResponse, error) {
	return gcm.SendHttp(s.apiKey, gcm.HttpMessage{To: newClientTopic, Data: gcm.Data{actionKey: broadcastNewClient, "client": c}})
}

// Send the list of clients to the newly registered client
func (s *fpServer) sendClientList(c Client) (*gcm.HttpResponse, error) {
	message := &gcm.HttpMessage{To: c.RegistrationToken, Data: gcm.Data{actionKey: sendClientList, "clients": s.getClientList(c)}}
	response, err := gcm.SendHttp(s.apiKey, *message)
	if err == nil {
		s.checkResponse(message, response)
	}
	return response, err
}

func (s *fpServer) pingClient(d gcm.Data) (*gcm.HttpResponse, error) {
	response := &gcm.HttpResponse{}
	senderObject := &Client{}
	recipient := ""
	toVal, ok := d[toKey]
	if !ok {
		return response, errors.New("Error parsing recipient from ping message")
	}
	to, ok := toVal.(string)
	if !ok {
		return response, errors.New("Error parsing recipient from ping message")
	}
	senderVal, ok := d[senderKey]
	if !ok {
		return response, errors.New("Error parsing sender from ping message")
	}
	sender, ok := senderVal.(string)
	if !ok {
		return response, errors.New("Error parsing sender from ping message")
	}
	// If the server is the recipient of the ping, reply to the test ping, else route as requested
	if to == s.getServerGcmAddress() {
		d[toKey] = sender
		recipient = sender
		d[senderKey] = s.getServerGcmAddress()
		senderObject = s.clients.c[s.getServerGcmAddress()]
	} else {
		recipient = to
		senderObject = s.clients.c[sender]
	}
	if senderObject == nil {
		return response, errors.New("Sender is not a registered client")
	} else {
		// This notification will be delivered in the more convenient way according to the platform
		notification := &gcm.Notification{Body: createPingMessage(senderObject.Name), Title: pingTitle, Icon: androidIcon, Sound: "default",
			ClickAction: pingReceived}
		message := &gcm.HttpMessage{To: recipient, Data: d, Notification: *notification}
		response, err := gcm.SendHttp(s.apiKey, *message)
		if err == nil {
			s.checkResponse(message, response)
		}
		return response, err
	}
}

// Check if the Response contains canonical ids and update reg ids if needed
func (s *fpServer) checkResponse(m *gcm.HttpMessage, r *gcm.HttpResponse) {
	if r.CanonicalIds > 0 {
		if m.To != "" {
			s.updateRegistrationId(r.Results[0].RegistrationId, m.To)
		} else if len(m.RegistrationIds) > 0 {
			for i := 0; i < len(m.RegistrationIds); i++ {
				if r.Results[i].RegistrationId != "" {
					s.updateRegistrationId(r.Results[i].RegistrationId, m.RegistrationIds[i])
				}

			}
		}
	}
}

func (s *fpServer) updateRegistrationId(new string, old string) {
	swapClient := s.clients.c[old]
	s.clients.Lock()
	s.clients.c[new] = swapClient
	delete(s.clients.c, old)
	s.clients.Unlock()
}

// Transform the map of connected clients to an array of clients
func (s *fpServer) getClientList(c Client) []*Client {
	i := 0
	s.clients.RLock()
	cl := []*Client{}
	for k := range s.clients.c {
		if s.clients.c[k].RegistrationToken != c.RegistrationToken {
			cl = append(cl, s.clients.c[k])
			i++
		}
	}
	s.clients.RUnlock()
	return cl
}

// Load test data for the server
func (s *fpServer) loadTestData() {
	file, err := ioutil.ReadFile(*testData)
	if err != nil {
		log.Fatalf("Failed to read test data file: %v", err)
	}
	if err := json.Unmarshal(file, &s.clients.c); err != nil {
		log.Fatalf("Failed to unmarshal test data: %v", err)
	}
}

func (s *fpServer) getServerGcmAddress() string {
	return fmt.Sprintf("%s@gcm.googleapis.com", s.senderId)
}

// Factory method for the fpServer
func newServer(apiKey, senderId string) *fpServer {
	clients := &Clients{c: make(map[string]*Client)}
	s := &fpServer{apiKey: apiKey, senderId: senderId, clients: *clients}
	if *testData != "" {
		s.loadTestData()
	}
	serverAddress := s.getServerGcmAddress()
	s.clients.c[serverAddress] = &Client{"Larry", serverAddress, "https://lh3.googleusercontent.com/-Y86IN-vEObo/AAAAAAAAAAI/AAAAAAADO1I/QzjOGHq5kNQ/photo.jpg?sz=50"}
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
