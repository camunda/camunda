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
	"github.com/golang/mock/gomock"
	"github.com/stretchr/testify/suite"
	"github.com/zeebe-io/zeebe/clients/go/entities"
	"github.com/zeebe-io/zeebe/clients/go/mock_pb"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"io"
	"math"
	"sync"
	"testing"
	"time"
)

type JobPollerSuite struct {
	suite.Suite
	ctrl      *gomock.Controller
	client    *mock_pb.MockGatewayClient
	poller    jobPoller
	waitGroup sync.WaitGroup
}

func (suite *JobPollerSuite) BeforeTest(suiteName, testName string) {
	suite.ctrl = gomock.NewController(suite.T())
	suite.client = mock_pb.NewMockGatewayClient(suite.ctrl)
	suite.poller = jobPoller{
		client:         suite.client,
		request:        pb.ActivateJobsRequest{},
		requestTimeout: utils.DefaultTestTimeout,
		maxJobsActive:  DefaultJobWorkerMaxJobActive,
		pollInterval:   DefaultJobWorkerPollInterval,
		jobQueue:       make(chan entities.Job),
		workerFinished: make(chan bool),
		closeSignal:    make(chan struct{}),
		remaining:      0,
		threshold:      int(math.Round(float64(DefaultJobWorkerMaxJobActive) * DefaultJobWorkerPollThreshold)),
	}
	suite.waitGroup.Add(1)
}

func (suite *JobPollerSuite) AfterTest(suiteName, testName string) {
	defer suite.ctrl.Finish()

	close(suite.poller.closeSignal)

	c := make(chan struct{})
	go func() {
		suite.waitGroup.Wait()
		close(c)
	}()

	for {
		select {
		case <-c:
			return
		case <-suite.poller.jobQueue:
			break // drop jobs
		case <-time.After(utils.DefaultTestTimeout):
			suite.FailNow("Failed to wait for job poller to close")
		}
	}
}

func TestJobPollerSuite(t *testing.T) {
	suite.Run(t, new(JobPollerSuite))
}

func (suite *JobPollerSuite) TestShouldPollAfterPollIntervalIfThresholdIsReached() {
	// given
	suite.poller.pollInterval = 250 * time.Millisecond
	suite.poller.maxJobsActive = 10
	suite.poller.threshold = 4

	gomock.InOrder(
		suite.client.EXPECT().ActivateJobs(gomock.Any(), &utils.RpcTestMsg{Msg: &pb.ActivateJobsRequest{
			MaxJobsToActivate: 10,
		}}).Return(suite.singleJobStream(), nil),
		suite.client.EXPECT().ActivateJobs(gomock.Any(), &utils.RpcTestMsg{Msg: &pb.ActivateJobsRequest{
			MaxJobsToActivate: 9,
		}}).Return(suite.singleJobStream(), nil),
	)

	// when
	go suite.poller.poll(&suite.waitGroup)

	// then two jobs are received (initial poll and after pollInterval)
	suite.consumeJob()
	suite.consumeJob()
}

func (suite *JobPollerSuite) TestShouldNotPollAfterIntervalIfNotThreshold() {
	// given
	suite.poller.pollInterval = 250 * time.Millisecond
	suite.poller.maxJobsActive = 10
	suite.poller.remaining = 6
	suite.poller.threshold = 4

	suite.client.EXPECT().ActivateJobs(gomock.Any(), &utils.RpcTestMsg{Msg: &pb.ActivateJobsRequest{
		MaxJobsToActivate: 4,
	}}).Return(suite.singleJobStream(), nil)

	// when
	go suite.poller.poll(&suite.waitGroup)

	// then one job is received after inital poll and no further poll
	suite.consumeJob()
}

func (suite *JobPollerSuite) TestShoulPolldAfterJobFinsihedIfThresholdIsReached() {
	// given
	suite.poller.maxJobsActive = 10
	suite.poller.threshold = 4

	gomock.InOrder(
		suite.client.EXPECT().ActivateJobs(gomock.Any(), &utils.RpcTestMsg{Msg: &pb.ActivateJobsRequest{
			MaxJobsToActivate: 10,
		}}).Return(suite.singleJobStream(), nil),
		suite.client.EXPECT().ActivateJobs(gomock.Any(), &utils.RpcTestMsg{Msg: &pb.ActivateJobsRequest{
			MaxJobsToActivate: 10,
		}}).Return(suite.singleJobStream(), nil),
	)

	// when
	go suite.poller.poll(&suite.waitGroup)

	// then two jobs are received (initial poll and poll after job finished)
	suite.completeJob()
	suite.consumeJob()
}

func (suite *JobPollerSuite) TestShouldNotPollAfterJobIsFinishedIfNotThreshold() {
	// given
	suite.poller.maxJobsActive = 10
	suite.poller.remaining = 6
	suite.poller.threshold = 4

	suite.client.EXPECT().ActivateJobs(gomock.Any(), &utils.RpcTestMsg{Msg: &pb.ActivateJobsRequest{
		MaxJobsToActivate: 4,
	}}).Return(suite.singleJobStream(), nil)

	// when
	go suite.poller.poll(&suite.waitGroup)

	// then one job is received after inital poll and no further poll
	suite.completeJob()
}

func (suite *JobPollerSuite) TestShouldIgnoreRequestFailure() {
	// given
	suite.poller.maxJobsActive = 10
	suite.poller.remaining = 5
	suite.poller.threshold = 4

	gomock.InOrder(
		suite.client.EXPECT().ActivateJobs(gomock.Any(), gomock.Any()).Return(nil, io.ErrClosedPipe),
		suite.client.EXPECT().ActivateJobs(gomock.Any(), gomock.Any()).Return(suite.singleJobStream(), nil),
	)

	// when
	go suite.poller.poll(&suite.waitGroup)

	// then signal job finished to poll again
	suite.poller.workerFinished <- true

	// should receive another job
	suite.consumeJob()
}

func (suite *JobPollerSuite) TestShouldIgnoreStreamFailure() {
	// given
	suite.poller.maxJobsActive = 10
	suite.poller.remaining = 4
	suite.poller.threshold = 4

	gomock.InOrder(
		suite.client.EXPECT().ActivateJobs(gomock.Any(), gomock.Any()).Return(suite.streamFailureAfterFirstJob(), nil),
		suite.client.EXPECT().ActivateJobs(gomock.Any(), gomock.Any()).Return(suite.streamFailureAfterFirstJob(), nil),
	)

	// when
	go suite.poller.poll(&suite.waitGroup)

	// then signal job finished to poll again
	suite.completeJob()

	// should receive another job from broken stream
	suite.consumeJob()
}

func (suite *JobPollerSuite) singleJobStream() pb.Gateway_ActivateJobsClient {
	stream := mock_pb.NewMockGateway_ActivateJobsClient(suite.ctrl)
	gomock.InOrder(
		stream.EXPECT().Recv().Return(&pb.ActivateJobsResponse{
			Jobs: []*pb.ActivatedJob{
				{},
			},
		}, nil),
		stream.EXPECT().Recv().Return(nil, io.EOF),
	)

	return stream
}

func (suite *JobPollerSuite) streamFailureAfterFirstJob() pb.Gateway_ActivateJobsClient {
	stream := mock_pb.NewMockGateway_ActivateJobsClient(suite.ctrl)
	gomock.InOrder(
		stream.EXPECT().Recv().Return(&pb.ActivateJobsResponse{
			Jobs: []*pb.ActivatedJob{
				{},
			},
		}, nil),
		stream.EXPECT().Recv().Return(nil, io.ErrClosedPipe),
	)
	return stream
}

func (suite *JobPollerSuite) consumeJob() {
	<-suite.poller.jobQueue
}

func (suite *JobPollerSuite) completeJob() {
	suite.consumeJob()
	suite.poller.workerFinished <- true
}
