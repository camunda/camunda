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
	"io"
	"log"
	"time"

	"github.com/camunda/zeebe/clients/go/v8/pkg/entities"
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
)

type StreamJobsConsumer chan<- entities.Job

type DispatchStreamJobsCommand interface {
	RequestTimeout(time.Duration) DispatchStreamJobsCommand
	Send(ctx context.Context) error
}

type StreamJobsCommandStep1 interface {
	JobType(string) StreamJobsCommandStep2
}

type StreamJobsCommandStep2 interface {
	Consumer(StreamJobsConsumer) StreamJobsCommandStep3
}

type StreamJobsCommandStep3 interface {
	DispatchStreamJobsCommand

	TenantIds(...string) StreamJobsCommandStep3
	Timeout(time.Duration) StreamJobsCommandStep3
	WorkerName(string) StreamJobsCommandStep3
	FetchVariables(...string) StreamJobsCommandStep3
}

type StreamJobsCommand struct {
	Command
	request        pb.StreamActivatedJobsRequest
	consumer       StreamJobsConsumer
	requestTimeout time.Duration
}

func (cmd *StreamJobsCommand) JobType(jobType string) StreamJobsCommandStep2 {
	cmd.request.Type = jobType
	return cmd
}

func (cmd *StreamJobsCommand) Consumer(consumer StreamJobsConsumer) StreamJobsCommandStep3 {
	cmd.consumer = consumer
	return cmd
}

func (cmd *StreamJobsCommand) TenantIds(tenantIds ...string) StreamJobsCommandStep3 {
	cmd.request.TenantIds = tenantIds
	return cmd
}

func (cmd *StreamJobsCommand) Timeout(timeout time.Duration) StreamJobsCommandStep3 {
	cmd.request.Timeout = int64(timeout / time.Millisecond)
	return cmd
}

func (cmd *StreamJobsCommand) WorkerName(workerName string) StreamJobsCommandStep3 {
	cmd.request.Worker = workerName
	return cmd
}

func (cmd *StreamJobsCommand) FetchVariables(fetchVariables ...string) StreamJobsCommandStep3 {
	cmd.request.FetchVariable = fetchVariables
	return cmd
}

func (cmd *StreamJobsCommand) RequestTimeout(requestTimeout time.Duration) DispatchStreamJobsCommand {
	cmd.requestTimeout = requestTimeout
	return cmd
}

func (cmd *StreamJobsCommand) Send(ctx context.Context) error {
	if cmd.requestTimeout > 0 {
		streamContext, cancelCtx := context.WithTimeout(ctx, cmd.requestTimeout)
		ctx = streamContext
		defer cancelCtx()
	}

	stream, err := cmd.openStream(ctx)
	if err != nil {
		return err
	}

	for {
		job, err := stream.Recv()
		if err != nil {
			if err == io.EOF {
				break
			}

			if !cmd.shouldRetry(ctx, err) {
				return err
			}

			stream, err = cmd.openStream(ctx)
			if err != nil {
				log.Printf("Failed to reopen job stream: %v\n", err)
				return err
			}
		}

		cmd.consumer <- entities.Job{ActivatedJob: job}
	}

	return nil
}

func (cmd *StreamJobsCommand) openStream(ctx context.Context) (pb.Gateway_StreamActivatedJobsClient, error) {
	stream, err := cmd.gateway.StreamActivatedJobs(ctx, &cmd.request)

	if err != nil {
		if cmd.shouldRetry(ctx, err) {
			return cmd.openStream(ctx)
		}

		return nil, err
	}

	return stream, nil
}

func NewStreamJobsCommand(gateway pb.GatewayClient, pred retryPredicate) StreamJobsCommandStep1 {
	return &StreamJobsCommand{
		request: pb.StreamActivatedJobsRequest{
			Timeout: DefaultJobTimeoutInMs,
			Worker:  DefaultJobWorkerName,
		},
		Command: Command{
			gateway:     gateway,
			shouldRetry: pred,
		},
	}
}
