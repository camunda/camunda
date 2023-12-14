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
	command   mockStreamJobsCommand
	backoff   mockBackoffSupplier
	streamer  jobStreamer
	jobQueue  chan entities.Job
	waitGroup sync.WaitGroup
}

func (s *JobStreamerSuite) BeforeTest(_, _ string) {
	s.jobQueue = make(chan entities.Job)
	s.command = mockStreamJobsCommand{
		sendChan: make(chan context.Context, 10),
	}
	s.backoff = mockBackoffSupplier{}
	s.streamer = jobStreamer{
		request:         &s.command,
		workerFinished:  make(chan bool),
		closeSignal:     make(chan struct{}),
		backoffSupplier: &s.backoff,
	}
	s.waitGroup.Add(1)
}

func (s *JobStreamerSuite) AfterTest(_, _ string) {
	close(s.streamer.closeSignal)

	c := make(chan struct{})
	go func() {
		s.waitGroup.Wait()
		close(c)
	}()

	for {
		select {
		case <-c:
			return
		case <-time.After(utils.DefaultTestTimeout):
			s.FailNow("Failed to wait for job streamer to close")
		}
	}
}

func TestJobStreamerSuite(t *testing.T) {
	suite.Run(t, new(JobStreamerSuite))
}

func (s *JobStreamerSuite) TestShouldNotOpenStreamIfClosed() {
	// given
	// since we aren't going to stream, the wait group has to be decremented
	// to allow the AfterTest to run properly
	finished := make(chan bool)
	s.streamer.close()

	// when - should not hang because the stream is closed
	go func() {
		s.streamer.stream(&s.waitGroup)
		finished <- true
		close(finished)
	}()

	// then
	select {
	case <-finished:
		s.EqualValues(0, s.command.sendCount)
	case <-time.After(10 * time.Second):
		s.FailNow("Timed out waiting for stream call to finish")
	}
}

func (s *JobStreamerSuite) TestShouldStopStreamOnCloseSignal() {
	// given
	var ctx context.Context
	s.command.setSendChanBuffer(0)

	// when - should not hang because the stream is closed
	go s.streamer.stream(&s.waitGroup)
	select {
	case ctx = <-s.command.sendChan:
		s.streamer.closeSignal <- struct{}{}
	case <-time.After(10 * time.Second):
		s.FailNow("Timed out waiting for stream call to finish")
	}

	// then
	select {
	case <-ctx.Done():
		// closing is async, so there will be for sure a second call
		s.EqualValues(2, s.command.sendCount)
	case <-time.After(10 * time.Second):
		s.FailNow("Context was not canceled even though the streamer was closed via a signal")
	}
}

func (s *JobStreamerSuite) TestShouldRecreateStreamOnCompleted() {
	// given
	var ctx context.Context
	// will block on the second send call
	s.command.setSendChanBuffer(0)

	// when - should not hang because the stream is closed
	go s.streamer.stream(&s.waitGroup)
	select {
	// capture second call context
	case <-s.command.sendChan:
		select {
		case ctx = <-s.command.sendChan:
			s.streamer.closeSignal <- struct{}{}
		case <-time.After(10 * time.Second):
			s.FailNow("Timed out waiting for stream call to finish")
		}
	case <-time.After(10 * time.Second):
		s.FailNow("Timed out waiting for stream call to finish")
	}

	// then
	select {
	case <-ctx.Done():
		s.EqualValues(2, s.command.sendCount)
		s.EqualValues(0, s.backoff.calledCount)
	case <-time.After(10 * time.Second):
		s.FailNow("Context was not canceled even though the streamer was closed via a signal")
	}
}

func (s *JobStreamerSuite) TestShouldRecreateStreamOnErrorWithBackoff() {
	// given
	var ctx context.Context
	s.command.err = errors.New("Foo")
	s.backoff.delaySequence = []time.Duration{1, 2 * time.Hour}
	// will block on the second send call
	s.command.setSendChanBuffer(1)

	// when - should not hang because the stream is closed
	go s.streamer.stream(&s.waitGroup)
	select {
	// capture second call context
	case <-s.command.sendChan:
		select {
		case ctx = <-s.command.sendChan:
			s.streamer.closeSignal <- struct{}{}
		case <-time.After(10 * time.Second):
			s.FailNow("Timed out waiting for stream call to finish")
		}
	case <-time.After(10 * time.Second):
		s.FailNow("Timed out waiting for stream call to finish")
	}

	// then

	select {
	case <-ctx.Done():
		s.EqualValues(2, s.backoff.calledCount)
		s.Require().Len(s.backoff.currentRetryDelays, 2)
		s.EqualValues(0, s.backoff.currentRetryDelays[0])
		s.EqualValues(1, s.backoff.currentRetryDelays[1])
	case <-time.After(10 * time.Second):
		s.FailNow("Context was not canceled even though the streamer was closed via a signal")
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
