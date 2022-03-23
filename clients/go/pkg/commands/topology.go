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
//

package commands

import (
	"context"
	"github.com/camunda/zeebe/clients/go/pkg/pb"
)

type TopologyCommand struct {
	Command
}

func (cmd *TopologyCommand) Send(ctx context.Context) (*pb.TopologyResponse, error) {
	response, err := cmd.gateway.Topology(ctx, &pb.TopologyRequest{})
	if cmd.shouldRetry(ctx, err) {
		return cmd.Send(ctx)
	}

	return response, err
}

func NewTopologyCommand(gateway pb.GatewayClient, pred retryPredicate) *TopologyCommand {
	return &TopologyCommand{
		Command{
			gateway:     gateway,
			shouldRetry: pred,
		},
	}
}
