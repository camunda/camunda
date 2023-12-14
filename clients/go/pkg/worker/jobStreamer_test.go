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
	"errors"
	"sync"
	"testing"
	"time"

	"github.com/camunda/zeebe/clients/go/v8/internal/utils"
	"github.com/camunda/zeebe/clients/go/v8/pkg/commands"
	"github.com/camunda/zeebe/clients/go/v8/pkg/entities"
	"github.com/stretchr/testify/suite"
)

type JobStreamerSuite struct {
	suite.Suite
}

func TestJobStreamerSuite(t *testing.T) {
	suite.Run(t, new(JobStreamerSuite))
}

func (s *JobStreamerSuite) TestShouldNotOpenStreamIfClosed() {
	// given
	// since we aren't going to stream, the wait group has to be decremented
	// to allow the AfterTest to run properly
	state := newTestState()
	defer state.close(s)
	finished := make(chan bool)
	state.streamer.close()

	// when - should not hang because the stream is closed
	go func() {
		state.streamer.stream(state.waitGroup)
		finished <- true
		close(finished)
	}()

	// then
	select {
	case <-finished:
		s.EqualValues(0, state.command.sendCount)
	case <-time.After(10 * time.Second):
		s.FailNow("Timed out waiting for stream call to finish")
	}
}

func (s *JobStreamerSuite) TestShouldStopStreamOnCloseSignal() {
	// given
	var ctx context.Context
	state := newTestState()
	defer state.close(s)
	state.command.setSendChanBuffer(0)

	// when - should not hang because the stream is closed
	go state.streamer.stream(state.waitGroup)
	select {
	case ctx = <-state.command.sendChan:
		state.streamer.closeSignal <- struct{}{}
	case <-time.After(10 * time.Second):
		s.FailNow("Timed out waiting for stream call to finish")
	}

	// then
	select {
	case <-ctx.Done():
		// since recreating the stream is async, this may be 1 or 2 depending on the
		// scheduling
		s.GreaterOrEqual(state.command.sendCount, 1)
		s.LessOrEqual(state.command.sendCount, 2)
	case <-time.After(10 * time.Second):
		s.FailNow("Context was not canceled even though the streamer was closed via a signal")
	}
}

func (s *JobStreamerSuite) TestShouldRecreateStreamOnCompleted() {
	// given
	var ctx context.Context
	state := newTestState()
	defer state.close(s)
	// will block on the second send call
	state.command.setSendChanBuffer(0)

	// when - should not hang because the stream is closed
	go state.streamer.stream(state.waitGroup)
	select {
	// capture second call context
	case <-state.command.sendChan:
		select {
		case ctx = <-state.command.sendChan:
			state.streamer.closeSignal <- struct{}{}
		case <-time.After(10 * time.Second):
			s.FailNow("Timed out waiting for stream call to finish")
		}
	case <-time.After(10 * time.Second):
		s.FailNow("Timed out waiting for stream call to finish")
	}

	// then
	select {
	case <-ctx.Done():
		s.EqualValues(2, state.command.sendCount)
		s.EqualValues(0, state.backoff.calledCount)
	case <-time.After(10 * time.Second):
		s.FailNow("Context was not canceled even though the streamer was closed via a signal")
	}
}

func (s *JobStreamerSuite) TestShouldRecreateStreamOnErrorWithBackoff() {
	// given
	var ctx context.Context
	state := newTestState()
	defer state.close(s)
	state.command.err = errors.New("Foo")
	state.backoff.delaySequence = []time.Duration{1, 2 * time.Hour}
	// will block on the second send call
	state.command.setSendChanBuffer(2)

	// when - should not hang because the stream is closed
	go state.streamer.stream(state.waitGroup)
	select {
	// capture second call context
	case <-state.command.sendChan:
		select {
		case ctx = <-state.command.sendChan:
			state.streamer.closeSignal <- struct{}{}
		case <-time.After(10 * time.Second):
			s.FailNow("Timed out waiting for stream call to finish")
		}
	case <-time.After(10 * time.Second):
		s.FailNow("Timed out waiting for stream call to finish")
	}

	// then

	select {
	case <-ctx.Done():
		s.EqualValues(2, state.backoff.calledCount)
		s.Require().Len(state.backoff.currentRetryDelays, 2)
		s.EqualValues(0, state.backoff.currentRetryDelays[0])
		s.EqualValues(1, state.backoff.currentRetryDelays[1])
	case <-time.After(10 * time.Second):
		s.FailNow("Context was not canceled even though the streamer was closed via a signal")
	}
}

type testState struct {
	command   *mockStreamJobsCommand
	backoff   *mockBackoffSupplier
	streamer  *jobStreamer
	jobQueue  chan entities.Job
	waitGroup *sync.WaitGroup
}

func newTestState() *testState {
	command := mockStreamJobsCommand{
		sendChan: make(chan context.Context, 10),
	}
	backoff := mockBackoffSupplier{}
	state := testState{
		command: &command,
		backoff: &backoff,
		streamer: &jobStreamer{
			request:         &command,
			workerFinished:  make(chan bool),
			closeSignal:     make(chan struct{}),
			backoffSupplier: &backoff,
		},
		jobQueue:  make(chan entities.Job),
		waitGroup: &sync.WaitGroup{},
	}

	state.waitGroup.Add(1)

	return &state
}

func (state *testState) close(suite *JobStreamerSuite) {
	close(state.streamer.closeSignal)

	c := make(chan struct{})
	go func() {
		state.waitGroup.Wait()
		close(c)
	}()

	for {
		select {
		case <-c:
			return
		case <-time.After(utils.DefaultTestTimeout):
			suite.FailNow("Failed to wait for job streamer to close")
		}
	}
}

type mockStreamJobsCommand struct {
	sendCount int
	err       error
	sendChan  chan context.Context
}

type mockBackoffSupplier struct {
	calledCount        int
	delaySequence      []time.Duration
	currentRetryDelays []time.Duration
}

func (m *mockStreamJobsCommand) RequestTimeout(_ time.Duration) commands.DispatchStreamJobsCommand {
	panic(errors.New("Not implemented yet as not expected to be invoked"))
}

func (m *mockStreamJobsCommand) Send(ctx context.Context) error {
	m.sendCount++
	m.sendChan <- ctx
	return m.err
}

func (m *mockStreamJobsCommand) setSendChanBuffer(count int) {
	sendChan := m.sendChan
	m.sendChan = make(chan context.Context, count)
	close(sendChan)
}

func (m *mockBackoffSupplier) SupplyRetryDelay(currentRetryDelay time.Duration) time.Duration {
	m.currentRetryDelays = append(m.currentRetryDelays, currentRetryDelay)

	index := m.calledCount
	m.calledCount++
	if index < len(m.delaySequence) {
		return m.delaySequence[index]
	}

	return 0
}
