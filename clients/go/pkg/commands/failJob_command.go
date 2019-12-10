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

type DispatchFailJobCommand interface {
	Send() (*pb.FailJobResponse, error)
}

type FailJobCommandStep1 interface {
	JobKey(int64) FailJobCommandStep2
}

type FailJobCommandStep2 interface {
	Retries(int32) FailJobCommandStep3
}

type FailJobCommandStep3 interface {
	DispatchFailJobCommand
	ErrorMessage(string) FailJobCommandStep3
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

func (cmd *FailJobCommand) ErrorMessage(errorMessage string) FailJobCommandStep3 {
	cmd.request.ErrorMessage = errorMessage
	return cmd
}

func (cmd *FailJobCommand) Send() (*pb.FailJobResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	return cmd.gateway.FailJob(ctx, &cmd.request)
}

func NewFailJobCommand(gateway pb.GatewayClient, requestTimeout time.Duration, retryPredicate func(error) bool) FailJobCommandStep1 {
	return &FailJobCommand{
		Command: Command{gateway: gateway,
			requestTimeout: requestTimeout,
			retryPredicate: retryPredicate,
		},
	}
}
