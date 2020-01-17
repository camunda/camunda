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
)

type GatewayVersionCommand interface {
	Send(context.Context) (string, error)
}

type gatewayVersionCmd struct {
	Command
	req *pb.GatewayVersionRequest
}

func (c *gatewayVersionCmd) Send(ctx context.Context) (string, error) {
	resp, err := c.gateway.GatewayVersion(ctx, c.req)

	if err != nil {
		if c.shouldRetry(ctx, err) {
			return c.Send(ctx)
		}

		return "", err
	}

	return resp.Version, nil
}

func NewGatewayVersionCommand(client pb.GatewayClient, retryPred retryPredicate) GatewayVersionCommand {
	return &gatewayVersionCmd{
		Command: Command{
			gateway:     client,
			shouldRetry: retryPred,
		},
		req: &pb.GatewayVersionRequest{},
	}
}
