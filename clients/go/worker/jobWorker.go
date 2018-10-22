package worker

import (
	"github.com/zeebe-io/zeebe/clients/go/commands"
	"github.com/zeebe-io/zeebe/clients/go/entities"
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
	closePoller     chan bool
	closeDispatcher chan bool
	closeWait       *sync.WaitGroup
}

func (controller jobWorkerController) Close() {
	controller.closePoller <- true
	controller.closeDispatcher <- true
	controller.AwaitClose()
}

func (controller jobWorkerController) AwaitClose() {
	controller.closeWait.Wait()
}
