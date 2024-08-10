// Copyright © 2018 Camunda Services GmbH (info@camunda.com)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
