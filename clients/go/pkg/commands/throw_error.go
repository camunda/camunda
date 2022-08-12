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
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
)

type ThrowErrorCommandStep1 interface {
	JobKey(int64) ThrowErrorCommandStep2
}

type ThrowErrorCommandStep2 interface {
	ErrorCode(string) DispatchThrowErrorCommand
}

type DispatchThrowErrorCommand interface {
	TenantId(string) DispatchThrowErrorCommand
	ErrorMessage(string) DispatchThrowErrorCommand
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

func (c *ThrowErrorCommand) TenantId(tenantId string) DispatchThrowErrorCommand {
	c.request.TenantId = tenantId
	return c
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
			gateway:     gateway,
			shouldRetry: pred,
		},
	}

}
