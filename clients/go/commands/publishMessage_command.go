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

type PublishMessageCommandStep1 interface {
	MessageName(string) PublishMessageCommandStep2
}

type PublishMessageCommandStep2 interface {
	CorrelationKey(string) PublishMessageCommandStep3
}

type PublishMessageCommandStep3 interface {
	DispatchPublishMessageCommand

	MessageId(string) PublishMessageCommandStep3
	TimeToLive(duration time.Duration) PublishMessageCommandStep3

	// Expected to be valid JSON string
	PayloadFromString(string) (PublishMessageCommandStep3, error)

	// Expected to construct a valid JSON string
	PayloadFromStringer(fmt.Stringer) (PublishMessageCommandStep3, error)

	// Expected that object is JSON serializable
	PayloadFromObject(interface{}) (PublishMessageCommandStep3, error)
	PayloadFromObjectIgnoreOmitempty(interface{}) (PublishMessageCommandStep3, error)
	PayloadFromMap(map[string]interface{}) (PublishMessageCommandStep3, error)
}

type DispatchPublishMessageCommand interface {
	Send() (*pb.PublishMessageResponse, error)
}

type PublishMessageCommand struct {
	utils.SerializerMixin

	request        *pb.PublishMessageRequest
	gateway        pb.GatewayClient
	requestTimeout time.Duration
}

func (cmd *PublishMessageCommand) MessageId(messageId string) PublishMessageCommandStep3 {
	cmd.request.MessageId = messageId
	return cmd
}

func (cmd *PublishMessageCommand) PayloadFromObject(payload interface{}) (PublishMessageCommandStep3, error) {
	value, err := cmd.AsJson("payload", payload, false)
	if err != nil {
		return nil, err
	}

	cmd.request.Payload = value
	return cmd, nil
}

func (cmd *PublishMessageCommand) PayloadFromObjectIgnoreOmitempty(payload interface{}) (PublishMessageCommandStep3, error) {
	value, err := cmd.AsJson("payload", payload, true)
	if err != nil {
		return nil, err
	}

	cmd.request.Payload = value
	return cmd, nil
}

func (cmd *PublishMessageCommand) PayloadFromMap(payload map[string]interface{}) (PublishMessageCommandStep3, error) {
	return cmd.PayloadFromObject(payload)
}

func (cmd *PublishMessageCommand) PayloadFromString(payload string) (PublishMessageCommandStep3, error) {
	err := cmd.Validate("payload", payload)
	if err != nil {
		return nil, err
	}

	cmd.request.Payload = payload
	return cmd, nil
}

func (cmd *PublishMessageCommand) PayloadFromStringer(payload fmt.Stringer) (PublishMessageCommandStep3, error) {
	return cmd.PayloadFromString(payload.String())
}

func (cmd *PublishMessageCommand) TimeToLive(duration time.Duration) PublishMessageCommandStep3 {
	cmd.request.TimeToLive = int64(duration / time.Millisecond)
	return cmd
}

func (cmd *PublishMessageCommand) CorrelationKey(key string) PublishMessageCommandStep3 {
	cmd.request.CorrelationKey = key
	return cmd
}

func (cmd *PublishMessageCommand) MessageName(name string) PublishMessageCommandStep2 {
	cmd.request.Name = name
	return cmd
}

func (cmd *PublishMessageCommand) Send() (*pb.PublishMessageResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	return cmd.gateway.PublishMessage(ctx, cmd.request)
}

func NewPublishMessageCommand(gateway pb.GatewayClient, requestTimeout time.Duration) PublishMessageCommandStep1 {
	return &PublishMessageCommand{
		SerializerMixin: utils.NewJsonStringSerializer(),
		request:         &pb.PublishMessageRequest{},
		gateway:         gateway,
		requestTimeout:  requestTimeout,
	}
}
