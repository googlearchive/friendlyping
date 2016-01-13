golang Friendly Ping
--

## Installation
Install go-gcm:

	$ go get github.com/google/go-gcm
	$ go install github.com/google/go-gcm



## Usage

    $ go run server.go -testData testdata/clients.json \
    -apiKey <your_server_api_key> -senderId <your_sender_id> 
	
You can get your Server API Key and Sender ID for your project from its **Download and install configuration** page at https://developers.google.com/mobile/add.
