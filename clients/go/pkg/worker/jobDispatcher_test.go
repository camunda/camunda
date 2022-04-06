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
	"github.com/camunda/zeebe/clients/go/v8/internal/utils"
	"github.com/camunda/zeebe/clients/go/v8/pkg/commands"
	"github.com/camunda/zeebe/clients/go/v8/pkg/entities"
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
	"github.com/stretchr/testify/suite"
	"sync"
	"testing"
	"time"
)

type JobDispatcherSuite struct {
	suite.Suite
	client          jobClientStub
	dispatcher      jobDispatcher
	awaitHandler    chan bool
	continueHandler chan bool
	waitGroup       sync.WaitGroup
}

func (suite *JobDispatcherSuite) BeforeTest(string, string) {
	suite.client = jobClientStub{}
	suite.dispatcher = jobDispatcher{
		jobQueue:       make(chan entities.Job),
		workerFinished: make(chan bool),
		closeSignal:    make(chan struct{}),
	}
	suite.awaitHandler = make(chan bool)
	suite.continueHandler = make(chan bool)
	suite.waitGroup.Add(1)
}

func (suite *JobDispatcherSuite) AfterTest(string, string) {
	c := make(chan struct{})
	go func() {
		suite.waitGroup.Wait()
		close(c)
	}()

	for {
		select {
		case <-c:
			return
		case <-suite.awaitHandler:
			// complete jobs
			suite.completeJob(false)
		case <-time.After(utils.DefaultTestTimeout):
			suite.FailNow("Failed to wait for job dispatcher to close")
		}
	}
}

func TestJobDispatcherSuite(t *testing.T) {
	suite.Run(t, new(JobDispatcherSuite))
}

func (suite *JobDispatcherSuite) TestShouldCloseDispatcherWhenWaitingForJob() {
	// given
	handler := suite.newSyncedJobHandler()

	go suite.dispatcher.run(&suite.client, handler, 1, &suite.waitGroup)

	// when
	suite.dispatcher.jobQueue <- entities.Job{}
	// await handler received job so the dispatcher will wait for next job
	<-suite.awaitHandler

	// then
	close(suite.dispatcher.closeSignal)
	suite.completeJob(false)
}

func (suite *JobDispatcherSuite) TestShouldCloseDispatcherWhenWaitingForWorker() {
	// given
	handler := suite.newSyncedJobHandler()

	go suite.dispatcher.run(&suite.client, handler, 1, &suite.waitGroup)

	// when
	suite.dispatcher.jobQueue <- entities.Job{}
	// await the handler ist blocked
	<-suite.awaitHandler
	// await the dispatcher picked up the next job and waits for worker
	suite.dispatcher.jobQueue <- entities.Job{}

	// then
	close(suite.dispatcher.closeSignal)
	suite.completeJob(false)
}

func (suite *JobDispatcherSuite) TestShouldReuseHandler() {
	// given
	handler := suite.newSyncedJobHandler()

	go suite.dispatcher.run(&suite.client, handler, 1, &suite.waitGroup)

	// when
	suite.dispatcher.jobQueue <- entities.Job{}
	suite.dispatcher.jobQueue <- entities.Job{}

	// then
	suite.completeJob(true)
	suite.completeJob(true)

	close(suite.dispatcher.closeSignal)
}

func (suite *JobDispatcherSuite) TestShouldPassClientAndJobToHandler() {
	// given
	var jobKey int64 = 123

	handler := func(client JobClient, job entities.Job) {
		suite.Assert().Equal(jobKey, job.Key)
		client.NewCompleteJobCommand()
	}

	go suite.dispatcher.run(&suite.client, handler, 1, &suite.waitGroup)

	// when
	suite.dispatcher.jobQueue <- entities.Job{ActivatedJob: &pb.ActivatedJob{Key: jobKey}}

	// then
	select {
	case <-suite.dispatcher.workerFinished:
		suite.Assert().True(suite.client.invoked)
	case <-time.After(utils.DefaultTestTimeout):
		suite.FailNow("Failed to wait for job handler invocation")
	}

	close(suite.dispatcher.closeSignal)
}

func (suite *JobDispatcherSuite) TestShouldHandleMultipleJobsInParallel() {
	// given
	concurrency := 4
	handler := suite.newSyncedJobHandler()

	go suite.dispatcher.run(&suite.client, handler, concurrency, &suite.waitGroup)

	// when
	for i := 0; i < concurrency; i++ {
		suite.dispatcher.jobQueue <- entities.Job{}
	}

	// then
	for i := 0; i < concurrency; i++ {
		suite.completeJob(true)
	}

	close(suite.dispatcher.closeSignal)
}

func (suite *JobDispatcherSuite) newSyncedJobHandler() func(JobClient, entities.Job) {
	return func(JobClient, entities.Job) {
		suite.awaitHandler <- true
		select {
		case <-suite.continueHandler:
			break
		case <-time.After(utils.DefaultTestTimeout):
			suite.FailNow("Failed to wait for job handler sync")
		}
	}
}

func (suite *JobDispatcherSuite) completeJob(syncHandler bool) {
	if syncHandler {
		<-suite.awaitHandler
	}
	suite.continueHandler <- true
	<-suite.dispatcher.workerFinished
}

type jobClientStub struct {
	invoked bool
}

func (stub *jobClientStub) NewCompleteJobCommand() commands.CompleteJobCommandStep1 {
	stub.invoked = true
	return nil
}

func (jobClientStub) NewFailJobCommand() commands.FailJobCommandStep1 {
	panic("implement me")
}
