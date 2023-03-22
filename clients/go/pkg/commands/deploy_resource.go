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

type DeployResourceCommand struct {
	Command
	request pb.DeployResourceRequest
}

func (cmd *DeployResourceCommand) AddResourceFile(path string) *DeployResourceCommand {
	b, err := os.ReadFile(path)
	if err != nil {
		log.Fatal(err)
	}
	return cmd.AddResource(b, path)
}

func (cmd *DeployResourceCommand) AddResource(definition []byte, name string) *DeployResourceCommand {
	cmd.request.Resources = append(cmd.request.Resources, &pb.Resource{Content: definition, Name: name})
	return cmd
}

func (cmd *DeployResourceCommand) Send(ctx context.Context) (*pb.DeployResourceResponse, error) {
	response, err := cmd.gateway.DeployResource(ctx, &cmd.request)
	if cmd.shouldRetry(ctx, err) {
		return cmd.Send(ctx)
	}

	return response, err
}

func NewDeployResourceCommand(gateway pb.GatewayClient, pred retryPredicate) *DeployResourceCommand {
	return &DeployResourceCommand{
		Command: Command{
			gateway:     gateway,
			shouldRetry: pred,
		},
	}
}
