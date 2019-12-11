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
	"github.com/zeebe-io/zeebe/clients/go/pkg/pb"
	"io/ioutil"
	"log"
	"time"
)

type DeployCommand struct {
	Command
	request pb.DeployWorkflowRequest
}

func (cmd *DeployCommand) AddResourceFile(path string) *DeployCommand {
	b, err := ioutil.ReadFile(path)
	if err != nil {
		log.Fatal(err)
	}
	return cmd.AddResource(b, path, pb.WorkflowRequestObject_FILE)
}

func (cmd *DeployCommand) AddResource(definition []byte, name string, resourceType pb.WorkflowRequestObject_ResourceType) *DeployCommand {
	cmd.request.Workflows = append(cmd.request.Workflows, &pb.WorkflowRequestObject{Definition: definition, Name: name, Type: resourceType})
	return cmd
}

func (cmd *DeployCommand) Send() (*pb.DeployWorkflowResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	response, err := cmd.gateway.DeployWorkflow(ctx, &cmd.request)
	if cmd.retryPredicate(err) {
		return cmd.Send()
	}

	return response, err
}

func NewDeployCommand(gateway pb.GatewayClient, requestTimeout time.Duration, retryPredicate func(error) bool) *DeployCommand {
	return &DeployCommand{
		Command: Command{
			gateway:        gateway,
			requestTimeout: requestTimeout,
			retryPredicate: retryPredicate,
		},
	}
}
