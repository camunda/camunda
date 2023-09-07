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
	"errors"
	"io"
	"log"
	"time"

	"github.com/camunda/zeebe/clients/go/v8/pkg/entities"
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
)

const (
	DefaultJobTimeout     = 5 * time.Minute
	DefaultJobTimeoutInMs = int64(DefaultJobTimeout / time.Millisecond)
	DefaultJobWorkerName  = "default"
)

type DispatchActivateJobsCommand interface {
	Send(ctx context.Context) ([]entities.Job, error)
}

type ActivateJobsCommandStep1 interface {
	JobType(string) ActivateJobsCommandStep2
}

type ActivateJobsCommandStep2 interface {
	MaxJobsToActivate(int32) ActivateJobsCommandStep3
}

type ActivateJobsCommandStep3 interface {
	DispatchActivateJobsCommand

	TenantIds(...string) ActivateJobsCommandStep3
	Timeout(time.Duration) ActivateJobsCommandStep3
	WorkerName(string) ActivateJobsCommandStep3
	FetchVariables(...string) ActivateJobsCommandStep3
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

func (cmd *ActivateJobsCommand) TenantIds(tenantIds ...string) ActivateJobsCommandStep3 {
	cmd.request.TenantIds = tenantIds
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

func (cmd *ActivateJobsCommand) Send(ctx context.Context) ([]entities.Job, error) {
	cmd.request.RequestTimeout = getLongPollingMillis(ctx)

	stream, err := cmd.openStream(ctx)
	if err != nil {
		return nil, err
	}

	var activatedJobs []entities.Job
	for {
		response, err := stream.Recv()
		if errors.Is(err, io.EOF) {
			break
		}
		if cmd.shouldRetry(ctx, err) {
			// the headers are outdated and need to be remade
			stream, err = cmd.openStream(ctx)
			if err != nil {
				log.Printf("Failed to reopen job polling stream: %v\n", err)
			}
			continue
		}

		if err != nil {
			return activatedJobs, err
		}
		for _, activatedJob := range response.Jobs {
			activatedJobs = append(activatedJobs, entities.Job{ActivatedJob: activatedJob})
		}
	}

	return activatedJobs, nil
}

func (cmd *ActivateJobsCommand) openStream(ctx context.Context) (pb.Gateway_ActivateJobsClient, error) {
	stream, err := cmd.gateway.ActivateJobs(ctx, &cmd.request)
	if err != nil {
		if cmd.shouldRetry(ctx, err) {
			return cmd.openStream(ctx)
		}
		return nil, err
	}

	return stream, nil
}
func NewActivateJobsCommand(gateway pb.GatewayClient, pred retryPredicate) ActivateJobsCommandStep1 {
	return &ActivateJobsCommand{
		request: pb.ActivateJobsRequest{
			Timeout: DefaultJobTimeoutInMs,
			Worker:  DefaultJobWorkerName,
		},
		Command: Command{
			gateway:     gateway,
			shouldRetry: pred,
		},
	}
}
