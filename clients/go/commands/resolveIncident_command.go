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
	"time"

	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
)

type DispatchResolveIncidentCommand interface {
	Send() (*pb.ResolveIncidentResponse, error)
}

type ResolveIncidentCommandStep1 interface {
	IncidentKey(int64) ResolveIncidentCommandStep2
}

type ResolveIncidentCommandStep2 interface {
	DispatchResolveIncidentCommand
}

type ResolveIncidentCommand struct {
	utils.SerializerMixin

	request        *pb.ResolveIncidentRequest
	gateway        pb.GatewayClient
	requestTimeout time.Duration
	retryPredicate func(error) bool
}

func (cmd *ResolveIncidentCommand) IncidentKey(incidentKey int64) ResolveIncidentCommandStep2 {
	cmd.request.IncidentKey = incidentKey
	return cmd
}

func (cmd *ResolveIncidentCommand) Send() (*pb.ResolveIncidentResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	response, err := cmd.gateway.ResolveIncident(ctx, cmd.request)
	if cmd.retryPredicate(err) {
		return cmd.Send()
	}

	return response, err
}

func NewResolveIncidentCommand(gateway pb.GatewayClient, requestTimeout time.Duration, retryPredicate func(error) bool) ResolveIncidentCommandStep1 {
	return &ResolveIncidentCommand{
		SerializerMixin: utils.NewJsonStringSerializer(),
		request:         &pb.ResolveIncidentRequest{},
		gateway:         gateway,
		requestTimeout:  requestTimeout,
		retryPredicate:  retryPredicate,
	}
}
