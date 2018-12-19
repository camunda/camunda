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
	"fmt"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"time"
)

type DispatchUpdatePayloadCommand interface {
	Send() (*pb.UpdateWorkflowInstancePayloadResponse, error)
}

type UpdatePayloadCommandStep1 interface {
	ElementInstanceKey(int64) UpdatePayloadCommandStep2
}

type UpdatePayloadCommandStep2 interface {
	PayloadFromString(string) (DispatchUpdatePayloadCommand, error)
	PayloadFromStringer(fmt.Stringer) (DispatchUpdatePayloadCommand, error)
	PayloadFromMap(map[string]interface{}) (DispatchUpdatePayloadCommand, error)
	PayloadFromObject(interface{}) (DispatchUpdatePayloadCommand, error)
	PayloadFromObjectIgnoreOmitempty(interface{}) (DispatchUpdatePayloadCommand, error)
}

type UpdatePayloadCommand struct {
	utils.SerializerMixin

	request        *pb.UpdateWorkflowInstancePayloadRequest
	gateway        pb.GatewayClient
	requestTimeout time.Duration
}

func (cmd *UpdatePayloadCommand) ElementInstanceKey(elementInstanceKey int64) UpdatePayloadCommandStep2 {
	cmd.request.ElementInstanceKey = elementInstanceKey
	return cmd
}

func (cmd *UpdatePayloadCommand) PayloadFromString(payload string) (DispatchUpdatePayloadCommand, error) {
	err := cmd.Validate("payload", payload)
	if err != nil {
		return nil, err
	}

	cmd.request.Payload = payload
	return cmd, nil
}

func (cmd *UpdatePayloadCommand) PayloadFromStringer(payload fmt.Stringer) (DispatchUpdatePayloadCommand, error) {
	return cmd.PayloadFromString(payload.String())
}

func (cmd *UpdatePayloadCommand) PayloadFromObject(payload interface{}) (DispatchUpdatePayloadCommand, error) {
	value, err := cmd.AsJson("payload", payload, false)
	if err != nil {
		return nil, err
	}

	cmd.request.Payload = value
	return cmd, nil
}

func (cmd *UpdatePayloadCommand) PayloadFromObjectIgnoreOmitempty(payload interface{}) (DispatchUpdatePayloadCommand, error) {
	value, err := cmd.AsJson("payload", payload, true)
	if err != nil {
		return nil, err
	}

	cmd.request.Payload = value
	return cmd, nil
}

func (cmd *UpdatePayloadCommand) PayloadFromMap(payload map[string]interface{}) (DispatchUpdatePayloadCommand, error) {
	return cmd.PayloadFromObject(payload)
}

func (cmd *UpdatePayloadCommand) Send() (*pb.UpdateWorkflowInstancePayloadResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	return cmd.gateway.UpdateWorkflowInstancePayload(ctx, cmd.request)
}

func NewUpdatePayloadCommand(gateway pb.GatewayClient, requestTimeout time.Duration) UpdatePayloadCommandStep1 {
	return &UpdatePayloadCommand{
		SerializerMixin: utils.NewJsonStringSerializer(),
		request:         &pb.UpdateWorkflowInstancePayloadRequest{},
		gateway:         gateway,
		requestTimeout:  requestTimeout,
	}
}
