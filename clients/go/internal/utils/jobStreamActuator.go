package utils

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

type RemoteJobStream struct {
	JobType   string                    `json:"jobType"`
	Metadata  RemoteJobStreamMetadata   `json:"metadata"`
	Consumers []RemoteJobStreamConsumer `json:"consumers"`
}

type RemoteJobStreamMetadata struct {
	Worker         string   `json:"worker"`
	Timeout        string   `json:"timeout"`
	FetchVariables []string `json:"fetchVariables"`
}

type RemoteJobStreamConsumer struct {
	ID       string `json:"id"`
	Receiver string `json:"receiver"`
}

// We use the worker name to differentiate which stream we await
func AwaitJobStreamExists(workerName string, monitoringAddress string) bool {
	streamExists := false
	for start := time.Now(); !streamExists && time.Since(start) < 10*time.Second; {
		response, err := http.Get(fmt.Sprintf("http://%s/actuator/jobstreams/remote", monitoringAddress))
		if err != nil {
			time.Sleep(time.Second)
			continue
		}

		remoteStreams := make([]RemoteJobStream, 1)
		responseData, err := io.ReadAll(response.Body)
		if err != nil {
			time.Sleep(time.Second)
			continue
		}

		err = json.Unmarshal(responseData, &remoteStreams)
		if err != nil {
			time.Sleep(time.Second)
			continue
		}

		for _, remoteStream := range remoteStreams {
			if remoteStream.Metadata.Worker == workerName {
				return true
			}
		}
	}

	return false
}
