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

package commands

import (
	"context"
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
	"log"
	"os"
)

type DeployCommand struct {
	Command
	request pb.DeployProcessRequest //nolint
}

func (cmd *DeployCommand) AddResourceFile(path string) *DeployCommand {
	b, err := os.ReadFile(path)
	if err != nil {
		log.Fatal(err)
	}
	return cmd.AddResource(b, path)
}

func (cmd *DeployCommand) AddResource(definition []byte, name string) *DeployCommand {
	process := &pb.ProcessRequestObject{Definition: definition, Name: name} //nolint
	cmd.request.Processes = append(cmd.request.Processes, process)
	return cmd
}

func (cmd *DeployCommand) Send(ctx context.Context) (*pb.DeployProcessResponse, error) { //nolint
	response, err := cmd.gateway.DeployProcess(ctx, &cmd.request) //nolint
	if cmd.shouldRetry(ctx, err) {
		return cmd.Send(ctx)
	}

	return response, err
}

// Deprecated: Use NewDeployResourceCommand instead. To be removed in 8.1.0.
func NewDeployCommand(gateway pb.GatewayClient, pred retryPredicate) *DeployCommand {
	return &DeployCommand{
		Command: Command{
			gateway:     gateway,
			shouldRetry: pred,
		},
	}
}
