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

package worker

import (
	"github.com/zeebe-io/zeebe/clients/go/commands"
	"github.com/zeebe-io/zeebe/clients/go/entities"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"log"
	"math"
	"sync"
	"time"
)

const (
	DefaultJobWorkerMaxJobActive  = 32
	DefaultJobWorkerConcurrency   = 4
	DefaultJobWorkerPollInterval  = 100 * time.Millisecond
	DefaultJobWorkerPollThreshold = 0.3
)

type JobWorkerBuilder struct {
	gatewayClient  pb.GatewayClient
	jobClient      JobClient
	request        pb.ActivateJobsRequest
	requestTimeout time.Duration

	handler       JobHandler
	maxJobsActive int
	concurrency   int
	pollInterval  time.Duration
	pollThreshold float64
}

type JobWorkerBuilderStep1 interface {
	// Set the type of jobs to work on
	JobType(string) JobWorkerBuilderStep2
}

type JobWorkerBuilderStep2 interface {
	// Set the handler to process jobs. The worker should complete or fail the job. The handler implementation
	// must be thread-safe.
	Handler(JobHandler) JobWorkerBuilderStep3
}

type JobWorkerBuilderStep3 interface {
	// Set the name of the worker owner
	Name(string) JobWorkerBuilderStep3
	// Set the duration no other worker should work on job activated by this worker
	Timeout(time.Duration) JobWorkerBuilderStep3
	// Set the maximum number of jobs which will be activated for this worker at the
	// same time.
	MaxJobsActive(int) JobWorkerBuilderStep3
	// Set the maximum number of concurrent spawned goroutines to complete jobs
	Concurrency(int) JobWorkerBuilderStep3
	// Set the maximal interval between polling for new jobs
	PollInterval(time.Duration) JobWorkerBuilderStep3
	// Set the threshold of buffered activated jobs before polling for new jobs, i.e. threshold * MaxJobsActive(int)
	PollThreshold(float64) JobWorkerBuilderStep3
	// Set list of variable names which should be fetched on job activation
	FetchVariables(...string) JobWorkerBuilderStep3
	// Open the job worker and start polling and handling jobs
	Open() JobWorker
}

func (builder *JobWorkerBuilder) JobType(jobType string) JobWorkerBuilderStep2 {
	builder.request.Type = jobType
	return builder
}

func (builder *JobWorkerBuilder) Handler(handler JobHandler) JobWorkerBuilderStep3 {
	builder.handler = handler
	return builder
}

func (builder *JobWorkerBuilder) Name(name string) JobWorkerBuilderStep3 {
	builder.request.Worker = name
	return builder
}

func (builder *JobWorkerBuilder) Timeout(timeout time.Duration) JobWorkerBuilderStep3 {
	builder.request.Timeout = int64(timeout / time.Millisecond)
	return builder
}

func (builder *JobWorkerBuilder) MaxJobsActive(maxJobsActive int) JobWorkerBuilderStep3 {
	if maxJobsActive > 0 {
		builder.maxJobsActive = maxJobsActive
	} else {
		log.Println("Ignoring invalid maximum", maxJobsActive, "which should be greater then for job worker and using instead", builder.maxJobsActive)
	}
	return builder
}

func (builder *JobWorkerBuilder) Concurrency(concurrency int) JobWorkerBuilderStep3 {
	if concurrency > 0 {
		builder.concurrency = concurrency
	} else {
		log.Println("Ignoring invalid concurrency", concurrency, "which should be greater then zero for job worker and using instead", builder.concurrency)
	}
	return builder
}

func (builder *JobWorkerBuilder) PollInterval(pollInterval time.Duration) JobWorkerBuilderStep3 {
	builder.pollInterval = pollInterval
	return builder
}

func (builder *JobWorkerBuilder) PollThreshold(pollThreshold float64) JobWorkerBuilderStep3 {
	if pollThreshold > 0 {
		builder.pollThreshold = pollThreshold
	} else {
		log.Println("Ignoring invalid poll threshold", pollThreshold, "which should be greater then zero for job worker and using instead", builder.concurrency)
	}
	return builder
}

func (builder *JobWorkerBuilder) FetchVariables(fetchVariables ...string) JobWorkerBuilderStep3 {
	builder.request.FetchVariable = fetchVariables
	return builder
}

func (builder *JobWorkerBuilder) Open() JobWorker {
	jobQueue := make(chan entities.Job, builder.maxJobsActive)
	workerFinished := make(chan bool, builder.maxJobsActive)
	closePoller := make(chan struct{})
	closeDispatcher := make(chan struct{})
	var closeWait sync.WaitGroup
	closeWait.Add(2)

	poller := jobPoller{
		client:         builder.gatewayClient,
		maxJobsActive:  builder.maxJobsActive,
		pollInterval:   builder.pollInterval,
		request:        builder.request,
		requestTimeout: builder.requestTimeout,

		jobQueue:       jobQueue,
		workerFinished: workerFinished,
		closeSignal:    closePoller,
		remaining:      0,
		threshold:      int(math.Round(float64(builder.maxJobsActive) * builder.pollThreshold)),
	}

	dispatcher := jobDispatcher{
		jobQueue:       jobQueue,
		workerFinished: workerFinished,
		closeSignal:    closeDispatcher,
	}

	go poller.poll(&closeWait)
	go dispatcher.run(builder.jobClient, builder.handler, builder.concurrency, &closeWait)

	return jobWorkerController{
		closePoller:     closePoller,
		closeDispatcher: closeDispatcher,
		closeWait:       &closeWait,
	}
}

func NewJobWorkerBuilder(gatewayClient pb.GatewayClient, jobClient JobClient, requestTimeout time.Duration) JobWorkerBuilderStep1 {
	return &JobWorkerBuilder{
		gatewayClient: gatewayClient,
		jobClient:     jobClient,
		maxJobsActive: DefaultJobWorkerMaxJobActive,
		concurrency:   DefaultJobWorkerConcurrency,
		pollInterval:  DefaultJobWorkerPollInterval,
		pollThreshold: DefaultJobWorkerPollThreshold,
		request: pb.ActivateJobsRequest{
			Timeout: commands.DefaultJobTimeoutInMs,
			Worker:  commands.DefaultJobWorkerName,
		},
		requestTimeout: requestTimeout,
	}
}
