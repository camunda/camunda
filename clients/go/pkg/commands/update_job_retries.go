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
	"github.com/camunda/zeebe/clients/go/pkg/pb"
)

const (
	DefaultJobRetries = 3
)

type DispatchUpdateJobRetriesCommand interface {
	Send(context.Context) (*pb.UpdateJobRetriesResponse, error)
}

type UpdateJobRetriesCommandStep1 interface {
	JobKey(int64) UpdateJobRetriesCommandStep2
}

type UpdateJobRetriesCommandStep2 interface {
	DispatchUpdateJobRetriesCommand

	Retries(int32) DispatchUpdateJobRetriesCommand
}

type UpdateJobRetriesCommand struct {
	Command
	request pb.UpdateJobRetriesRequest
}

func (cmd *UpdateJobRetriesCommand) JobKey(jobKey int64) UpdateJobRetriesCommandStep2 {
	cmd.request.JobKey = jobKey
	return cmd
}

func (cmd *UpdateJobRetriesCommand) Retries(retries int32) DispatchUpdateJobRetriesCommand {
	cmd.request.Retries = retries
	return cmd
}

func (cmd *UpdateJobRetriesCommand) Send(ctx context.Context) (*pb.UpdateJobRetriesResponse, error) {
	response, err := cmd.gateway.UpdateJobRetries(ctx, &cmd.request)
	if cmd.shouldRetry(ctx, err) {
		return cmd.Send(ctx)
	}

	return response, err
}

func NewUpdateJobRetriesCommand(gateway pb.GatewayClient, pred retryPredicate) UpdateJobRetriesCommandStep1 {
	return &UpdateJobRetriesCommand{
		request: pb.UpdateJobRetriesRequest{
			Retries: DefaultJobRetries,
		},
		Command: Command{
			gateway:     gateway,
			shouldRetry: pred,
		},
	}
}
