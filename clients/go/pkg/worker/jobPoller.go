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
	"github.com/zeebe-io/zeebe/clients/go/pkg/entities"
	"github.com/zeebe-io/zeebe/clients/go/pkg/pb"
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
	stream, err := poller.client.ActivateJobs(ctx, poller.request)
	if err != nil {
		log.Println("Failed to request jobs for worker", poller.request.Worker, err)
		return
	}

	for {
		response, err := stream.Recv()
		if err != nil {
			if err != io.EOF && status.Code(err) != codes.ResourceExhausted {
				log.Println("Failed to activate jobs for worker", poller.request.Worker, err)
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

func (poller *jobPoller) setJobsRemainingCountMetric(count int) {
	if poller.metrics != nil {
		poller.metrics.SetJobsRemainingCount(poller.request.GetType(), count)
	}
}
