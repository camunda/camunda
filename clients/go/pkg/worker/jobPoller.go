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
	"context"
	"fmt"
	"github.com/camunda/zeebe/clients/go/pkg/entities"
	"github.com/camunda/zeebe/clients/go/pkg/pb"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"io"
	"log"
	"sync"
	"time"
)

type jobPoller struct {
	client         pb.GatewayClient
	request        *pb.ActivateJobsRequest
	requestTimeout time.Duration
	maxJobsActive  int
	pollInterval   time.Duration

	jobQueue       chan entities.Job
	workerFinished chan bool
	closeSignal    chan struct{}
	remaining      int
	threshold      int
	metrics        JobWorkerMetrics
	shouldRetry    func(context.Context, error) bool
}

func (poller *jobPoller) poll(closeWait *sync.WaitGroup) {
	defer closeWait.Done()

	// initial poll
	poller.activateJobs()

	for {
		select {
		// either a job was finished
		case <-poller.workerFinished:
			poller.remaining--
			poller.setJobsRemainingCountMetric(poller.remaining)
		// or the poll interval exceeded
		case <-time.After(poller.pollInterval):
		// or poller should stop
		case <-poller.closeSignal:
			poller.setJobsRemainingCountMetric(0)
			return
		}

		if poller.shouldActivateJobs() {
			poller.activateJobs()
		}
	}
}

func (poller *jobPoller) shouldActivateJobs() bool {
	return poller.remaining <= poller.threshold
}

func (poller *jobPoller) activateJobs() {
	ctx, cancel := context.WithTimeout(context.Background(), poller.requestTimeout)
	defer cancel()

	poller.request.MaxJobsToActivate = int32(poller.maxJobsActive - poller.remaining)
	stream, err := poller.openStream(ctx)
	if err != nil {
		log.Println(err.Error())
		return
	}

	for {
		response, err := stream.Recv()
		if err != nil {
			if poller.shouldRetry(ctx, err) {
				// the headers are outdated and need to be rebuilt
				stream, err = poller.openStream(ctx)
				if err != nil {
					log.Printf("Failed to reopen job polling stream: %v\n", err)
					break
				}
				continue
			}

			if err != io.EOF && status.Code(err) != codes.ResourceExhausted {
				log.Printf("Failed to activate jobs for worker '%s': %v\n", poller.request.Worker, err)
			}

			break
		}

		poller.remaining += len(response.Jobs)
		poller.setJobsRemainingCountMetric(poller.remaining)
		for _, job := range response.Jobs {
			poller.jobQueue <- entities.Job{ActivatedJob: job}
		}
	}
}

func (poller *jobPoller) openStream(ctx context.Context) (pb.Gateway_ActivateJobsClient, error) {
	stream, err := poller.client.ActivateJobs(ctx, poller.request)
	if err != nil {
		if poller.shouldRetry(ctx, err) {
			return poller.openStream(ctx)
		}
		return nil, fmt.Errorf("worker '%s' failed to open job stream: %w", poller.request.Worker, err)
	}

	return stream, nil
}

func (poller *jobPoller) setJobsRemainingCountMetric(count int) {
	if poller.metrics != nil {
		poller.metrics.SetJobsRemainingCount(poller.request.GetType(), count)
	}
}
