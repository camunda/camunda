package worker

import (
	"github.com/zeebe-io/zeebe/clients/go/entities"
	"sync"
)

type jobDispatcher struct {
	jobQueue       chan entities.Job
	workerFinished chan bool
	closeSignal    chan bool
}

func (dispatcher *jobDispatcher) run(client JobClient, handler JobHandler, concurrency int, closeWait *sync.WaitGroup) {
	defer closeWait.Done()

	// prepare for shutdown
	closeWorkers := make(chan bool, concurrency)
	var workersClosed sync.WaitGroup
	workersClosed.Add(concurrency)

	awaitClose := func() {
		for i := 0; i < concurrency; i++ {
			closeWorkers <- true
		}
		workersClosed.Wait()
	}

	// start concurrent workers
	workerQueue := make(chan chan entities.Job, concurrency)
	for i := 0; i < concurrency; i++ {
		go func() {
			defer workersClosed.Done()

			work := make(chan entities.Job, 1)
			for {
				workerQueue <- work
				select {
				case job := <-work:
					handler(client, job)
					dispatcher.workerFinished <- true
				case <-closeWorkers:
					return
				}
			}
		}()
	}

	for {
		// wait for job or close signal
		select {
		case job := <-dispatcher.jobQueue:
			select {
			// wait for worker or close signal
			case worker := <-workerQueue:
				worker <- job
			case <-dispatcher.closeSignal:
				awaitClose()
				return
			}
		case <-dispatcher.closeSignal:
			awaitClose()
			return
		}
	}
}
