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
	"github.com/zeebe-io/zeebe/clients/go/pkg/pb"
	"time"
)

type ThrowErrorCommandStep1 interface {
	JobKey(int64) ThrowErrorCommandStep2
}

type ThrowErrorCommandStep2 interface {
	ErrorCode(string) DispatchThrowErrorCommand
}

type DispatchThrowErrorCommand interface {
	ErrorMessage(string) DispatchThrowErrorCommand
	Send() (*pb.ThrowErrorResponse, error)
}

type ThrowErrorCommand struct {
	requestTimeout time.Duration
	request        pb.ThrowErrorRequest
	retryPredicate func(error) bool
	gateway        pb.GatewayClient
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

func (c *ThrowErrorCommand) Send() (*pb.ThrowErrorResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), c.requestTimeout)
	defer cancel()

	return c.gateway.ThrowError(ctx, &c.request)
}

func NewThrowErrorCommand(gateway pb.GatewayClient, requestTimeout time.Duration, retryPredicate func(error) bool) ThrowErrorCommandStep1 {
	return &ThrowErrorCommand{
		requestTimeout: requestTimeout,
		gateway:        gateway,
		retryPredicate: retryPredicate,
	}

}
