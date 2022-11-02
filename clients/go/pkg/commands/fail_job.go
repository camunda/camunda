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

type DispatchFailJobCommand interface {
	Send(context.Context) (*pb.FailJobResponse, error)
}

type FailJobCommandStep1 interface {
	JobKey(int64) FailJobCommandStep2
}

type FailJobCommandStep2 interface {
	Retries(int32) FailJobCommandStep3
}

type FailJobCommandStep3 interface {
	DispatchFailJobCommand
	RetryBackoff(retryBackoff time.Duration) FailJobCommandStep3
	ErrorMessage(string) FailJobCommandStep3

	VariablesFromString(string) (DispatchFailJobCommand, error)
	VariablesFromStringer(fmt.Stringer) (DispatchFailJobCommand, error)
	VariablesFromMap(map[string]interface{}) (DispatchFailJobCommand, error)
	VariablesFromObject(interface{}) (DispatchFailJobCommand, error)
	VariablesFromObjectIgnoreOmitempty(interface{}) (DispatchFailJobCommand, error)
}

type FailJobCommand struct {
	Command
	request pb.FailJobRequest
}

func (cmd *FailJobCommand) JobKey(jobKey int64) FailJobCommandStep2 {
	cmd.request.JobKey = jobKey
	return cmd
}

func (cmd *FailJobCommand) Retries(retries int32) FailJobCommandStep3 {
	cmd.request.Retries = retries
	return cmd
}

func (cmd *FailJobCommand) RetryBackoff(retryBackoff time.Duration) FailJobCommandStep3 {
	cmd.request.RetryBackOff = retryBackoff.Milliseconds()
	return cmd
}

func (cmd *FailJobCommand) ErrorMessage(errorMessage string) FailJobCommandStep3 {
	cmd.request.ErrorMessage = errorMessage
	return cmd
}

func (cmd *FailJobCommand) VariablesFromString(variables string) (DispatchFailJobCommand, error) {
	err := cmd.mixin.Validate("variables", variables)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = variables
	return cmd, nil
}

func (cmd *FailJobCommand) VariablesFromStringer(variables fmt.Stringer) (DispatchFailJobCommand, error) {
	return cmd.VariablesFromString(variables.String())
}

func (cmd *FailJobCommand) VariablesFromObject(variables interface{}) (DispatchFailJobCommand, error) {
	value, err := cmd.mixin.AsJSON("variables", variables, false)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = value
	return cmd, nil
}

func (cmd *FailJobCommand) VariablesFromObjectIgnoreOmitempty(variables interface{}) (DispatchFailJobCommand, error) {
	value, err := cmd.mixin.AsJSON("variables", variables, true)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = value
	return cmd, nil
}

func (cmd *FailJobCommand) VariablesFromMap(variables map[string]interface{}) (DispatchFailJobCommand, error) {
	return cmd.VariablesFromObject(variables)
}

func (cmd *FailJobCommand) Send(ctx context.Context) (*pb.FailJobResponse, error) {
	response, err := cmd.gateway.FailJob(ctx, &cmd.request)
	if cmd.shouldRetry(ctx, err) {
		return cmd.Send(ctx)
	}

	return response, err
}

func NewFailJobCommand(gateway pb.GatewayClient, pred retryPredicate) FailJobCommandStep1 {
	return &FailJobCommand{
		Command: Command{
			mixin:       utils.NewJSONStringSerializer(),
			gateway:     gateway,
			shouldRetry: pred,
		},
	}
}
