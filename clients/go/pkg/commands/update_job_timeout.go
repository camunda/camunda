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
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
)

type DispatchUpdateJobTimeoutCommand interface {
	Send(context.Context) (*pb.UpdateJobTimeoutResponse, error)
}

type UpdateJobTimeoutCommandStep1 interface {
	JobKey(int64) UpdateJobTimeoutCommandStep2
}

type UpdateJobTimeoutCommandStep2 interface {
	DispatchUpdateJobTimeoutCommand

	Timeout(int64) DispatchUpdateJobTimeoutCommand
}

type UpdateJobTimeoutCommand struct {
	Command
	request pb.UpdateJobTimeoutRequest
}

func (cmd *UpdateJobTimeoutCommand) JobKey(jobKey int64) UpdateJobTimeoutCommandStep2 {
	cmd.request.JobKey = jobKey
	return cmd
}

func (cmd *UpdateJobTimeoutCommand) Timeout(timeout int64) DispatchUpdateJobTimeoutCommand {
	cmd.request.Timeout = timeout
	return cmd
}

func (cmd *UpdateJobTimeoutCommand) Send(ctx context.Context) (*pb.UpdateJobTimeoutResponse, error) {
	response, err := cmd.gateway.UpdateJobTimeout(ctx, &cmd.request)
	if cmd.shouldRetry(ctx, err) {
		return cmd.Send(ctx)
	}

	return response, err
}

func NewUpdateJobTimeoutCommand(gateway pb.GatewayClient, pred retryPredicate) UpdateJobTimeoutCommandStep1 {
	return &UpdateJobTimeoutCommand{
		request: pb.UpdateJobTimeoutRequest{
			Timeout: DefaultJobTimeoutInMs,
		},
		Command: Command{
			gateway:     gateway,
			shouldRetry: pred,
		},
	}
}
