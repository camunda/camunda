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
	"github.com/zeebe-io/zeebe/clients/go/pkg/entities"
	"github.com/zeebe-io/zeebe/clients/go/pkg/pb"
	"io"
	"time"
)

const (
	DefaultJobTimeout     = 5 * time.Minute
	DefaultJobTimeoutInMs = int64(DefaultJobTimeout / time.Millisecond)
	DefaultJobWorkerName  = "default"
	RequestTimeoutOffset  = 10 * time.Second
)

type DispatchActivateJobsCommand interface {
	Send() ([]entities.Job, error)
}

type ActivateJobsCommandStep1 interface {
	JobType(string) ActivateJobsCommandStep2
}

type ActivateJobsCommandStep2 interface {
	MaxJobsToActivate(int32) ActivateJobsCommandStep3
}

type ActivateJobsCommandStep3 interface {
	DispatchActivateJobsCommand

	Timeout(time.Duration) ActivateJobsCommandStep3
	WorkerName(string) ActivateJobsCommandStep3
	FetchVariables(...string) ActivateJobsCommandStep3
	RequestTimeout(time.Duration) ActivateJobsCommandStep3
}

type ActivateJobsCommand struct {
	Command
	request pb.ActivateJobsRequest
}

func (cmd *ActivateJobsCommand) JobType(jobType string) ActivateJobsCommandStep2 {
	cmd.request.Type = jobType
	return cmd
}

func (cmd *ActivateJobsCommand) MaxJobsToActivate(maxJobsToActivate int32) ActivateJobsCommandStep3 {
	cmd.request.MaxJobsToActivate = maxJobsToActivate
	return cmd
}

func (cmd *ActivateJobsCommand) Timeout(timeout time.Duration) ActivateJobsCommandStep3 {
	cmd.request.Timeout = int64(timeout / time.Millisecond)
	return cmd
}

func (cmd *ActivateJobsCommand) WorkerName(workerName string) ActivateJobsCommandStep3 {
	cmd.request.Worker = workerName
	return cmd
}

func (cmd *ActivateJobsCommand) FetchVariables(fetchVariables ...string) ActivateJobsCommandStep3 {
	cmd.request.FetchVariable = fetchVariables
	return cmd
}

func (cmd *ActivateJobsCommand) RequestTimeout(timeout time.Duration) ActivateJobsCommandStep3 {
	cmd.request.RequestTimeout = int64(timeout / time.Millisecond)
	cmd.requestTimeout = timeout + RequestTimeoutOffset
	return cmd
}

func (cmd *ActivateJobsCommand) Send() ([]entities.Job, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	stream, err := cmd.gateway.ActivateJobs(ctx, &cmd.request)
	if err != nil {
		if cmd.retryPredicate(err) {
			return cmd.Send()
		}
		return nil, err
	}

	var activatedJobs []entities.Job

	for {
		response, err := stream.Recv()
		if err == io.EOF {
			break
		}
		if err != nil {
			return activatedJobs, err
		}
		for _, activatedJob := range response.Jobs {
			activatedJobs = append(activatedJobs, entities.Job{*activatedJob})
		}
	}

	return activatedJobs, nil
}

func NewActivateJobsCommand(gateway pb.GatewayClient, requestTimeout time.Duration, retryPredicate func(error) bool) ActivateJobsCommandStep1 {
	return &ActivateJobsCommand{
		request: pb.ActivateJobsRequest{
			Timeout:        DefaultJobTimeoutInMs,
			Worker:         DefaultJobWorkerName,
			RequestTimeout: int64(requestTimeout / time.Millisecond),
		},
		Command: Command{
			gateway:        gateway,
			requestTimeout: requestTimeout + RequestTimeoutOffset,
			retryPredicate: retryPredicate,
		},
	}
}
