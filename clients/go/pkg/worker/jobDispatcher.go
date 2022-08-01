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

package worker

import (
	"github.com/camunda/zeebe/clients/go/v8/pkg/entities"
	"sync"
)

type jobDispatcher struct {
	jobQueue       chan entities.Job
	workerFinished chan bool
	closeSignal    chan struct{}
}

func (dispatcher *jobDispatcher) run(client JobClient, handler JobHandler, concurrency int, closeWait *sync.WaitGroup) {
	defer closeWait.Done()

	// prepare for shutdown
	closeWorkers := make(chan struct{})
	var workersClosed sync.WaitGroup
	workersClosed.Add(concurrency)

	defer func() {
		close(closeWorkers)
		workersClosed.Wait()
	}()

	// start concurrent workers
	workerQueue := make(chan chan entities.Job, concurrency)
	for i := 0; i < concurrency; i++ {
		go func() {
			defer workersClosed.Done()

			work := make(chan entities.Job, 1)
		workerLoop:
			for {
				workerQueue <- work
				select {
				case job := <-work:
					handler(client, job)
					dispatcher.workerFinished <- true
				case <-closeWorkers:
					break workerLoop
				}
			}
		}()
	}

loop:
	for {
		// wait for job or close signal
		select {
		case job := <-dispatcher.jobQueue:
			select {
			// wait for worker or close signal
			case worker := <-workerQueue:
				worker <- job
			case <-dispatcher.closeSignal:
				break loop
			}
		case <-dispatcher.closeSignal:
			break loop
		}
	}
}
