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

type DispatchCompleteJobCommand interface {
	Send(context.Context) (*pb.CompleteJobResponse, error)
}

type CompleteJobCommandStep1 interface {
	JobKey(int64) CompleteJobCommandStep2
}

type CompleteJobCommandStep2 interface {
	DispatchCompleteJobCommand

	TenantId(string) DispatchCompleteJobCommand
	VariablesFromString(string) (DispatchCompleteJobCommand, error)
	VariablesFromStringer(fmt.Stringer) (DispatchCompleteJobCommand, error)
	VariablesFromMap(map[string]interface{}) (DispatchCompleteJobCommand, error)
	VariablesFromObject(interface{}) (DispatchCompleteJobCommand, error)
	VariablesFromObjectIgnoreOmitempty(interface{}) (DispatchCompleteJobCommand, error)
}

type CompleteJobCommand struct {
	Command
	request pb.CompleteJobRequest
}

func (cmd *CompleteJobCommand) JobKey(jobKey int64) CompleteJobCommandStep2 {
	cmd.request.JobKey = jobKey
	return cmd
}

func (cmd *CompleteJobCommand) VariablesFromString(variables string) (DispatchCompleteJobCommand, error) {
	err := cmd.mixin.Validate("variables", variables)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = variables
	return cmd, nil
}

func (cmd *CompleteJobCommand) VariablesFromStringer(variables fmt.Stringer) (DispatchCompleteJobCommand, error) {
	return cmd.VariablesFromString(variables.String())
}

func (cmd *CompleteJobCommand) VariablesFromObject(variables interface{}) (DispatchCompleteJobCommand, error) {
	value, err := cmd.mixin.AsJSON("variables", variables, false)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = value
	return cmd, nil
}

func (cmd *CompleteJobCommand) VariablesFromObjectIgnoreOmitempty(variables interface{}) (DispatchCompleteJobCommand, error) {
	value, err := cmd.mixin.AsJSON("variables", variables, true)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = value
	return cmd, nil
}

func (cmd *CompleteJobCommand) VariablesFromMap(variables map[string]interface{}) (DispatchCompleteJobCommand, error) {
	return cmd.VariablesFromObject(variables)
}

func (cmd *CompleteJobCommand) Send(ctx context.Context) (*pb.CompleteJobResponse, error) {
	response, err := cmd.gateway.CompleteJob(ctx, &cmd.request)
	if cmd.shouldRetry(ctx, err) {
		return cmd.Send(ctx)
	}

	return response, err
}

func (cmd *CompleteJobCommand) TenantId(tenantId string) DispatchCompleteJobCommand {
	cmd.request.TenantId = tenantId
	return cmd
}

func NewCompleteJobCommand(gateway pb.GatewayClient, pred retryPredicate) CompleteJobCommandStep1 {
	return &CompleteJobCommand{
		Command: Command{
			mixin:       utils.NewJSONStringSerializer(),
			gateway:     gateway,
			shouldRetry: pred,
		},
	}
}
