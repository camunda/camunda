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
	"github.com/camunda/zeebe/clients/go/v8/internal/utils"
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
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
	VariablesFromString(string) (PublishMessageCommandStep3, error)

	// Expected to construct a valid JSON string
	VariablesFromStringer(fmt.Stringer) (PublishMessageCommandStep3, error)

	// Expected that object is JSON serializable
	VariablesFromObject(interface{}) (PublishMessageCommandStep3, error)
	VariablesFromObjectIgnoreOmitempty(interface{}) (PublishMessageCommandStep3, error)
	VariablesFromMap(map[string]interface{}) (PublishMessageCommandStep3, error)
}

type DispatchPublishMessageCommand interface {
	Send(context.Context) (*pb.PublishMessageResponse, error)
}

type PublishMessageCommand struct {
	Command
	request pb.PublishMessageRequest
}

//nolint:revive
func (cmd *PublishMessageCommand) MessageId(messageId string) PublishMessageCommandStep3 {
	cmd.request.MessageId = messageId
	return cmd
}

func (cmd *PublishMessageCommand) VariablesFromObject(variables interface{}) (PublishMessageCommandStep3, error) {
	value, err := cmd.mixin.AsJSON("variables", variables, false)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = value
	return cmd, nil
}

func (cmd *PublishMessageCommand) VariablesFromObjectIgnoreOmitempty(variables interface{}) (PublishMessageCommandStep3, error) {
	value, err := cmd.mixin.AsJSON("variables", variables, true)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = value
	return cmd, nil
}

func (cmd *PublishMessageCommand) VariablesFromMap(variables map[string]interface{}) (PublishMessageCommandStep3, error) {
	return cmd.VariablesFromObject(variables)
}

func (cmd *PublishMessageCommand) VariablesFromString(variables string) (PublishMessageCommandStep3, error) {
	err := cmd.mixin.Validate("variables", variables)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = variables
	return cmd, nil
}

func (cmd *PublishMessageCommand) VariablesFromStringer(variables fmt.Stringer) (PublishMessageCommandStep3, error) {
	return cmd.VariablesFromString(variables.String())
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

func (cmd *PublishMessageCommand) Send(ctx context.Context) (*pb.PublishMessageResponse, error) {
	response, err := cmd.gateway.PublishMessage(ctx, &cmd.request)
	if cmd.shouldRetry(ctx, err) {
		return cmd.Send(ctx)
	}
	return response, err
}

func NewPublishMessageCommand(gateway pb.GatewayClient, pred retryPredicate) PublishMessageCommandStep1 {
	return &PublishMessageCommand{
		Command: Command{
			mixin:       utils.NewJSONStringSerializer(),
			gateway:     gateway,
			shouldRetry: pred,
		},
	}
}
