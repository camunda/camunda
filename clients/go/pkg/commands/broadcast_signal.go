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
	"fmt"
	"github.com/camunda/zeebe/clients/go/v8/internal/utils"
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
)

type DispatchBroadcastSignalCommand interface {
	Send(context.Context) (*pb.BroadcastSignalResponse, error)
}

type BroadcastSignalCommandStep1 interface {
	SignalName(string) BroadcastSignalCommandStep2
}

type BroadcastSignalCommandStep2 interface {
	DispatchBroadcastSignalCommand

	// Expected to be valid JSON string
	VariablesFromString(string) (BroadcastSignalCommandStep2, error)

	// Expected to construct a valid JSON string
	VariablesFromStringer(fmt.Stringer) (BroadcastSignalCommandStep2, error)

	// Expected that object is JSON serializable
	VariablesFromObject(interface{}) (BroadcastSignalCommandStep2, error)
	VariablesFromObjectIgnoreOmitempty(interface{}) (BroadcastSignalCommandStep2, error)
	VariablesFromMap(map[string]interface{}) (BroadcastSignalCommandStep2, error)
}

type BroadcastSignalCommand struct {
	Command
	request pb.BroadcastSignalRequest
}

func (cmd *BroadcastSignalCommand) VariablesFromString(variables string) (BroadcastSignalCommandStep2, error) {
	err := cmd.mixin.Validate("variables", variables)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = variables
	return cmd, nil
}

func (cmd *BroadcastSignalCommand) VariablesFromStringer(variables fmt.Stringer) (BroadcastSignalCommandStep2, error) {
	return cmd.VariablesFromString(variables.String())
}

func (cmd *BroadcastSignalCommand) VariablesFromObject(variables interface{}) (BroadcastSignalCommandStep2, error) {
	value, err := cmd.mixin.AsJSON("variables", variables, false)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = value
	return cmd, err
}

func (cmd *BroadcastSignalCommand) VariablesFromObjectIgnoreOmitempty(variables interface{}) (BroadcastSignalCommandStep2, error) {
	value, err := cmd.mixin.AsJSON("variables", variables, true)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = value
	return cmd, err
}

func (cmd *BroadcastSignalCommand) VariablesFromMap(variables map[string]interface{}) (BroadcastSignalCommandStep2, error) {
	return cmd.VariablesFromObject(variables)
}

func (cmd *BroadcastSignalCommand) SignalName(signalName string) BroadcastSignalCommandStep2 {
	cmd.request.SignalName = signalName
	return cmd
}

func (cmd *BroadcastSignalCommand) Send(ctx context.Context) (*pb.BroadcastSignalResponse, error) {
	response, err := cmd.gateway.BroadcastSignal(ctx, &cmd.request)
	if cmd.shouldRetry(ctx, err) {
		return cmd.Send(ctx)
	}

	return response, err
}

func NewBroadcastSignalCommand(gateway pb.GatewayClient, pred retryPredicate) BroadcastSignalCommandStep1 {
	return &BroadcastSignalCommand{
		Command: Command{
			mixin:       utils.NewJSONStringSerializer(),
			gateway:     gateway,
			shouldRetry: pred,
		},
	}
}
