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
	"github.com/zeebe-io/zeebe/clients/go/utils"

	"github.com/zeebe-io/zeebe/clients/go/pb"
	"time"
)

const LatestVersion = -1

type DispatchCreateInstanceCommand interface {
	Send() (*pb.CreateWorkflowInstanceResponse, error)
}

type CreateInstanceCommandStep1 interface {
	BPMNProcessId(string) CreateInstanceCommandStep2
	WorkflowKey(int64) CreateInstanceCommandStep3
}

type CreateInstanceCommandStep2 interface {
	Version(int32) CreateInstanceCommandStep3
	LatestVersion() CreateInstanceCommandStep3
}

type CreateInstanceCommandStep3 interface {
	DispatchCreateInstanceCommand

	// Expected to be valid JSON string
	VariablesFromString(string) (DispatchCreateInstanceCommand, error)

	// Expected to construct a valid JSON string
	VariablesFromStringer(fmt.Stringer) (DispatchCreateInstanceCommand, error)

	// Expected that object is JSON serializable
	VariablesFromObject(interface{}) (DispatchCreateInstanceCommand, error)
	VariablesFromObjectIgnoreOmitempty(interface{}) (DispatchCreateInstanceCommand, error)
	VariablesFromMap(map[string]interface{}) (DispatchCreateInstanceCommand, error)
}

type CreateInstanceCommand struct {
	utils.SerializerMixin

	request        *pb.CreateWorkflowInstanceRequest
	gateway        pb.GatewayClient
	requestTimeout time.Duration
}

func (cmd *CreateInstanceCommand) VariablesFromString(variables string) (DispatchCreateInstanceCommand, error) {
	err := cmd.Validate("variables", variables)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = variables
	return cmd, nil
}

func (cmd *CreateInstanceCommand) VariablesFromStringer(variables fmt.Stringer) (DispatchCreateInstanceCommand, error) {
	return cmd.VariablesFromString(variables.String())
}

func (cmd *CreateInstanceCommand) VariablesFromObject(variables interface{}) (DispatchCreateInstanceCommand, error) {
	value, err := cmd.AsJson("variables", variables, false)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = value
	return cmd, err
}

func (cmd *CreateInstanceCommand) VariablesFromObjectIgnoreOmitempty(variables interface{}) (DispatchCreateInstanceCommand, error) {
	value, err := cmd.AsJson("variables", variables, true)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = value
	return cmd, err
}

func (cmd *CreateInstanceCommand) VariablesFromMap(variables map[string]interface{}) (DispatchCreateInstanceCommand, error) {
	return cmd.VariablesFromObject(variables)
}

func (cmd *CreateInstanceCommand) Version(version int32) CreateInstanceCommandStep3 {
	cmd.request.Version = version
	return cmd
}

func (cmd *CreateInstanceCommand) LatestVersion() CreateInstanceCommandStep3 {
	cmd.request.Version = LatestVersion
	return cmd
}

func (cmd *CreateInstanceCommand) WorkflowKey(key int64) CreateInstanceCommandStep3 {
	cmd.request.WorkflowKey = key
	return cmd
}

func (cmd *CreateInstanceCommand) BPMNProcessId(id string) CreateInstanceCommandStep2 {
	cmd.request.BpmnProcessId = id
	return cmd
}

func (cmd *CreateInstanceCommand) Send() (*pb.CreateWorkflowInstanceResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	return cmd.gateway.CreateWorkflowInstance(ctx, cmd.request)
}

func NewCreateInstanceCommand(gateway pb.GatewayClient, requestTimeout time.Duration) CreateInstanceCommandStep1 {
	return &CreateInstanceCommand{
		SerializerMixin: utils.NewJsonStringSerializer(),
		request:         &pb.CreateWorkflowInstanceRequest{},
		gateway:         gateway,
		requestTimeout:  requestTimeout,
	}
}
