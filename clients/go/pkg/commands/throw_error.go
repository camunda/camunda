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
)

type ThrowErrorCommandStep1 interface {
	JobKey(int64) ThrowErrorCommandStep2
}

type ThrowErrorCommandStep2 interface {
	ErrorCode(string) DispatchThrowErrorCommand
}

type DispatchThrowErrorCommand interface {
	ErrorMessage(string) DispatchThrowErrorCommand

	VariablesFromString(string) (DispatchThrowErrorCommand, error)
	VariablesFromStringer(fmt.Stringer) (DispatchThrowErrorCommand, error)
	VariablesFromMap(map[string]interface{}) (DispatchThrowErrorCommand, error)
	VariablesFromObject(interface{}) (DispatchThrowErrorCommand, error)
	VariablesFromObjectIgnoreOmitempty(interface{}) (DispatchThrowErrorCommand, error)

	Send(context.Context) (*pb.ThrowErrorResponse, error)
}

type ThrowErrorCommand struct {
	Command
	request pb.ThrowErrorRequest
}

func (c *ThrowErrorCommand) JobKey(jobKey int64) ThrowErrorCommandStep2 {
	c.request.JobKey = jobKey
	return c
}

func (c *ThrowErrorCommand) ErrorCode(errorCode string) DispatchThrowErrorCommand {
	c.request.ErrorCode = errorCode
	return c
}

func (c *ThrowErrorCommand) ErrorMessage(errorMsg string) DispatchThrowErrorCommand {
	c.request.ErrorMessage = errorMsg
	return c
}

func (c *ThrowErrorCommand) VariablesFromString(variables string) (DispatchThrowErrorCommand, error) {
	err := c.mixin.Validate("variables", variables)
	if err != nil {
		return nil, err
	}

	c.request.Variables = variables
	return c, nil
}

func (c *ThrowErrorCommand) VariablesFromStringer(variables fmt.Stringer) (DispatchThrowErrorCommand, error) {
	return c.VariablesFromString(variables.String())
}

func (c *ThrowErrorCommand) VariablesFromObject(variables interface{}) (DispatchThrowErrorCommand, error) {
	value, err := c.mixin.AsJSON("variables", variables, false)
	if err != nil {
		return nil, err
	}

	c.request.Variables = value
	return c, nil
}

func (c *ThrowErrorCommand) VariablesFromObjectIgnoreOmitempty(variables interface{}) (DispatchThrowErrorCommand, error) {
	value, err := c.mixin.AsJSON("variables", variables, true)
	if err != nil {
		return nil, err
	}

	c.request.Variables = value
	return c, nil
}

func (c *ThrowErrorCommand) VariablesFromMap(variables map[string]interface{}) (DispatchThrowErrorCommand, error) {
	return c.VariablesFromObject(variables)
}

func (c *ThrowErrorCommand) Send(ctx context.Context) (*pb.ThrowErrorResponse, error) {
	response, err := c.gateway.ThrowError(ctx, &c.request)
	if c.shouldRetry(ctx, err) {
		return c.Send(ctx)
	}

	return response, err
}

func NewThrowErrorCommand(gateway pb.GatewayClient, pred retryPredicate) ThrowErrorCommandStep1 {
	return &ThrowErrorCommand{
		Command: Command{
			mixin:       utils.NewJSONStringSerializer(),
			gateway:     gateway,
			shouldRetry: pred,
		},
	}

}
