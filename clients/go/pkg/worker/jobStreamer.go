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
	"sync"
	"time"

	"github.com/camunda/zeebe/clients/go/v8/pkg/commands"
)

type jobStreamer struct {
	request         commands.DispatchStreamJobsCommand
	workerFinished  chan bool
	closeSignal     chan struct{}
	backoffSupplier BackoffSupplier
	retryDelay      time.Duration

	closedMutex sync.Mutex
	closed      bool

	streamMutex sync.Mutex
}

func (streamer *jobStreamer) stream(closeWait *sync.WaitGroup) {
	defer closeWait.Done()

	streamCtx, streamCancel := context.WithCancel(context.Background())
	defer streamCancel()

	streamClosed := make(chan error, 1)
	go streamer.openStream(streamCtx, streamClosed)

	var timer *time.Timer
	retryDelay := time.Duration(0)

	for {
		select {
		// stream was closed, check if we need to recreate it
		case err := <-streamClosed:
			if streamer.isClosed() {
				return
			}

			if err != nil {
				prevDelay := retryDelay
				retryDelay = streamer.backoffSupplier.SupplyRetryDelay(prevDelay)

				streamClosed = make(chan error, 1)
				timer = time.AfterFunc(retryDelay, func() { streamer.openStream(streamCtx, streamClosed) })
			} else {
				// if completed successfully just immediately recreate it, no need to back off
				streamClosed = make(chan error, 1)
				go streamer.openStream(streamCtx, streamClosed)
			}
		// TODO: increment job handled worker metric
		case <-streamer.workerFinished:
		// streamer was closed, most likely the worker is closing too
		case <-streamer.closeSignal:
			streamer.close()

			streamCancel()
			if timer != nil {
				timer.Stop()
			}

			return
		}
	}
}

func (streamer *jobStreamer) openStream(ctx context.Context, onClose chan<- error) {
	// only keep one open stream at a time
	var err error
	streamer.streamMutex.Lock()

	defer func() {
		streamer.streamMutex.Unlock()
		onClose <- err
		close(onClose)
	}()

	if streamer.isClosed() {
		return
	}

	err = streamer.request.Send(ctx)
}

func (streamer *jobStreamer) isClosed() bool {
	streamer.closedMutex.Lock()
	defer streamer.closedMutex.Unlock()

	return streamer.closed
}

func (streamer *jobStreamer) close() {
	streamer.closedMutex.Lock()
	defer streamer.closedMutex.Unlock()

	streamer.closed = true
}
