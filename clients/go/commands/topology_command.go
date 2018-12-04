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
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"time"
)

type TopologyCommand struct {
	gateway        pb.GatewayClient
	requestTimeout time.Duration
}

func (cmd *TopologyCommand) Send() (*pb.TopologyResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	request := &pb.TopologyRequest{}
	return cmd.gateway.Topology(ctx, request)
}

func NewTopologyCommand(gateway pb.GatewayClient, requestTimeout time.Duration) *TopologyCommand {
	return &TopologyCommand{
		gateway:        gateway,
		requestTimeout: requestTimeout,
	}
}
