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

type DispatchSetVariablesCommand interface {
	Local(bool) DispatchSetVariablesCommand
	Send() (*pb.SetVariablesResponse, error)
}

type SetVariablesCommandStep1 interface {
	ElementInstanceKey(int64) SetVariablesCommandStep2
}

type SetVariablesCommandStep2 interface {
	VariablesFromString(string) (DispatchSetVariablesCommand, error)
	VariablesFromStringer(fmt.Stringer) (DispatchSetVariablesCommand, error)
	VariablesFromMap(map[string]interface{}) (DispatchSetVariablesCommand, error)
	VariablesFromObject(interface{}) (DispatchSetVariablesCommand, error)
	VariablesFromObjectIgnoreOmitempty(interface{}) (DispatchSetVariablesCommand, error)
}

type SetVariablesCommand struct {
	utils.SerializerMixin

	request        *pb.SetVariablesRequest
	gateway        pb.GatewayClient
	requestTimeout time.Duration
}

func (cmd *SetVariablesCommand) ElementInstanceKey(elementInstanceKey int64) SetVariablesCommandStep2 {
	cmd.request.ElementInstanceKey = elementInstanceKey
	return cmd
}

func (cmd *SetVariablesCommand) VariablesFromString(variables string) (DispatchSetVariablesCommand, error) {
	err := cmd.Validate("variables", variables)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = variables
	return cmd, nil
}

func (cmd *SetVariablesCommand) VariablesFromStringer(variables fmt.Stringer) (DispatchSetVariablesCommand, error) {
	return cmd.VariablesFromString(variables.String())
}

func (cmd *SetVariablesCommand) VariablesFromObject(variables interface{}) (DispatchSetVariablesCommand, error) {
	value, err := cmd.AsJson("variables", variables, false)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = value
	return cmd, nil
}

func (cmd *SetVariablesCommand) VariablesFromObjectIgnoreOmitempty(variables interface{}) (DispatchSetVariablesCommand, error) {
	value, err := cmd.AsJson("variables", variables, true)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = value
	return cmd, nil
}

func (cmd *SetVariablesCommand) VariablesFromMap(variables map[string]interface{}) (DispatchSetVariablesCommand, error) {
	return cmd.VariablesFromObject(variables)
}

func (cmd *SetVariablesCommand) Local(local bool) DispatchSetVariablesCommand {
	cmd.request.Local = local
	return cmd
}

func (cmd *SetVariablesCommand) Send() (*pb.SetVariablesResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	return cmd.gateway.SetVariables(ctx, cmd.request)
}

func NewSetVariablesCommand(gateway pb.GatewayClient, requestTimeout time.Duration) SetVariablesCommandStep1 {
	return &SetVariablesCommand{
		SerializerMixin: utils.NewJsonStringSerializer(),
		request:         &pb.SetVariablesRequest{},
		gateway:         gateway,
		requestTimeout:  requestTimeout,
	}
}
