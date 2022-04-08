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
	"github.com/camunda/zeebe/clients/go/v8/pkg/commands"
	"github.com/camunda/zeebe/clients/go/v8/pkg/entities"
	"sync"
)

type JobClient interface {
	NewCompleteJobCommand() commands.CompleteJobCommandStep1
	NewFailJobCommand() commands.FailJobCommandStep1
}

type JobHandler func(client JobClient, job entities.Job)

type JobWorker interface {
	// Initiate graceful shutdown and awaits termination
	Close()
	// Await termination of worker
	AwaitClose()
}

type jobWorkerController struct {
	closePoller     chan struct{}
	closeDispatcher chan struct{}
	closeWait       *sync.WaitGroup
}

func (controller jobWorkerController) Close() {
	close(controller.closePoller)
	close(controller.closeDispatcher)
	controller.AwaitClose()
}

func (controller jobWorkerController) AwaitClose() {
	controller.closeWait.Wait()
}
