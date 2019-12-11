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

const (
	DefaultJobRetries = 3
)

type DispatchUpdateJobRetriesCommand interface {
	Send() (*pb.UpdateJobRetriesResponse, error)
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

func (cmd *UpdateJobRetriesCommand) Send() (*pb.UpdateJobRetriesResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	response, err := cmd.gateway.UpdateJobRetries(ctx, &cmd.request)
	if cmd.retryPredicate(err) {
		return cmd.Send()
	}

	return response, err
}

func NewUpdateJobRetriesCommand(gateway pb.GatewayClient, requestTimeout time.Duration, retryPredicate func(error) bool) UpdateJobRetriesCommandStep1 {
	return &UpdateJobRetriesCommand{
		request: pb.UpdateJobRetriesRequest{
			Retries: DefaultJobRetries,
		},
		Command: Command{
			gateway:        gateway,
			requestTimeout: requestTimeout,
			retryPredicate: retryPredicate,
		},
	}
}
