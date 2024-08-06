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
	"fmt"
	"io"
	"testing"
	"time"

	"github.com/camunda/camunda/clients/go/v8/internal/mock_pb"
	"github.com/camunda/camunda/clients/go/v8/internal/utils"
	"github.com/camunda/camunda/clients/go/v8/pkg/commands"
	"github.com/camunda/camunda/clients/go/v8/pkg/entities"
	"github.com/camunda/camunda/clients/go/v8/pkg/pb"
	"github.com/golang/mock/gomock"
	"github.com/stretchr/testify/assert"
	"google.golang.org/protobuf/proto"
)

// rpcMsg implements the gomock.Matcher interface
type rpcMsg struct {
	msg proto.Message
}

func (r *rpcMsg) Matches(msg interface{}) bool {
	m, ok := msg.(proto.Message)
	if !ok {
		return false
	}
	return proto.Equal(m, r.msg)
}

func (r *rpcMsg) String() string {
	return fmt.Sprintf("is %s", r.msg)
}

func TestJobWorkerActivateJobsDefault(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	stream := mock_pb.NewMockGateway_ActivateJobsClient(ctrl)

	request := &pb.ActivateJobsRequest{
		Type:              "foo",
		MaxJobsToActivate: DefaultJobWorkerMaxJobActive,
		Timeout:           commands.DefaultJobTimeoutInMs,
		Worker:            commands.DefaultJobWorkerName,
		RequestTimeout:    int64(utils.DefaultTestTimeout / time.Millisecond),
		TenantIds:         []string{commands.DefaultJobTenantID},
	}

	response := &pb.ActivateJobsResponse{
		Jobs: []*pb.ActivatedJob{
			{
				Key: 1,
			},
		},
	}

	stream.EXPECT().Recv().Return(response, nil).AnyTimes()

	client.EXPECT().ActivateJobs(gomock.Any(), &rpcMsg{msg: request}).Return(stream, nil)

	jobs := make(chan entities.Job, 1)

	NewJobWorkerBuilder(client, nil, nil).JobType("foo").Handler(func(client JobClient, job entities.Job) {
		jobs <- job
	}).RequestTimeout(utils.DefaultTestTimeout).Open()

	select {
	case job := <-jobs:
		expected := response.Jobs[0].Key
		if job.Key != expected {
			t.Error("Failed to received job", expected, "got", job.Key)
		}
	case <-time.After(utils.DefaultTestTimeout):
		t.Error("Failed to receive all jobs before timeout")
	}
}

func TestJobWorkerActivateJobsCustom(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	stream := mock_pb.NewMockGateway_ActivateJobsClient(ctrl)

	timeout := 7 * time.Minute

	request := &pb.ActivateJobsRequest{
		Type:              "foo",
		MaxJobsToActivate: 123,
		Timeout:           int64(timeout / time.Millisecond),
		Worker:            "fooWorker",
		RequestTimeout:    int64(utils.DefaultTestTimeout / time.Millisecond),
		TenantIds:         []string{commands.DefaultJobTenantID},
	}

	response := &pb.ActivateJobsResponse{
		Jobs: []*pb.ActivatedJob{
			{
				Key: 1,
			},
		},
	}

	stream.EXPECT().Recv().Return(response, nil).AnyTimes()

	client.EXPECT().ActivateJobs(gomock.Any(), &rpcMsg{msg: request}).Return(stream, nil)

	jobs := make(chan entities.Job, 1)

	NewJobWorkerBuilder(client, nil, nil).JobType("foo").Handler(func(client JobClient, job entities.Job) {
		jobs <- job
	}).MaxJobsActive(123).Timeout(timeout).RequestTimeout(utils.DefaultTestTimeout).Name("fooWorker").Open()

	select {
	case job := <-jobs:
		expected := response.Jobs[0].Key
		if job.Key != expected {
			t.Error("Failed to received job", expected, "got", job.Key)
		}
	case <-time.After(utils.DefaultTestTimeout):
		t.Error("Failed to receive all jobs before timeout")
	}
}

func TestJobWorkerStreamDisabled(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	stream := mock_pb.NewMockGateway_ActivateJobsClient(ctrl)

	timeout := 7 * time.Minute

	request := &pb.ActivateJobsRequest{
		Type:              "foo",
		MaxJobsToActivate: 123,
		Timeout:           int64(timeout / time.Millisecond),
		Worker:            "fooWorker",
		RequestTimeout:    int64(utils.DefaultTestTimeout / time.Millisecond),
		TenantIds:         []string{commands.DefaultJobTenantID},
	}

	response := &pb.ActivateJobsResponse{
		Jobs: []*pb.ActivatedJob{
			{
				Key: 1,
			},
		},
	}

	stream.EXPECT().Recv().Return(response, nil).AnyTimes()
	client.EXPECT().ActivateJobs(gomock.Any(), &rpcMsg{msg: request}).Return(stream, nil)
	client.EXPECT().StreamActivatedJobs(gomock.Any(), gomock.Any()).Times(0)

	jobs := make(chan entities.Job, 1)
	NewJobWorkerBuilder(client, nil, nil).JobType("foo").Handler(func(client JobClient, job entities.Job) {
		jobs <- job
	}).MaxJobsActive(123).Timeout(timeout).RequestTimeout(utils.DefaultTestTimeout).Name("fooWorker").Open()

	select {
	case job := <-jobs:
		expected := response.Jobs[0].Key
		if job.Key != expected {
			t.Error("Failed to received job", expected, "got", job.Key)
		}
	case <-time.After(utils.DefaultTestTimeout):
		t.Error("Failed to receive all jobs before timeout")
	}
}

func TestJobWorkerStreamEnabled(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	stream := mock_pb.NewMockGateway_StreamActivatedJobsClient(ctrl)
	activateJobsStream := mock_pb.NewMockGateway_ActivateJobsClient(ctrl)
	timeout := 7 * time.Minute
	request := &pb.StreamActivatedJobsRequest{
		Type:      "foo",
		Timeout:   int64(timeout / time.Millisecond),
		Worker:    "fooWorker",
		TenantIds: []string{commands.DefaultJobTenantID},
	}
	activateJobsRequest := &pb.ActivateJobsRequest{
		Type:              "foo",
		MaxJobsToActivate: 123,
		Timeout:           int64(timeout / time.Millisecond),
		Worker:            "fooWorker",
		RequestTimeout:    int64(utils.DefaultTestTimeout / time.Millisecond),
		TenantIds:         []string{commands.DefaultJobTenantID},
	}

	response := &pb.ActivatedJob{Key: 1}
	emptyActivateResponse := &pb.ActivateJobsResponse{}

	stream.EXPECT().Recv().Return(response, nil).AnyTimes()
	activateJobsStream.EXPECT().Recv().Return(emptyActivateResponse, nil).AnyTimes()
	client.EXPECT().StreamActivatedJobs(gomock.Any(), &rpcMsg{msg: request}).AnyTimes().Return(stream, nil)
	client.EXPECT().ActivateJobs(gomock.Any(), &rpcMsg{msg: activateJobsRequest}).Return(activateJobsStream, nil).AnyTimes()

	retryPred := func(ctx context.Context, err error) bool { return true }
	jobs := make(chan entities.Job, 1)
	NewJobWorkerBuilder(client, nil, retryPred).
		JobType("foo").
		Handler(func(client JobClient, job entities.Job) {
			jobs <- job
		}).
		MaxJobsActive(123).
		Timeout(timeout).
		RequestTimeout(utils.DefaultTestTimeout).
		Name("fooWorker").
		StreamEnabled(true).
		PollInterval(time.Minute).
		Open()

	select {
	case job := <-jobs:
		expected := response.Key
		if job.Key != expected {
			t.Error("Failed to received job", expected, "got", job.Key)
		}
	case <-time.After(utils.DefaultTestTimeout):
		t.Error("Failed to receive all jobs before timeout")
	}
}

func TestStreamingJobWorkerClose(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	stream := mock_pb.NewMockGateway_StreamActivatedJobsClient(ctrl)
	activateJobsStream := mock_pb.NewMockGateway_ActivateJobsClient(ctrl)
	timeout := 7 * time.Minute
	request := &pb.StreamActivatedJobsRequest{
		Type:      "foo",
		Timeout:   int64(timeout / time.Millisecond),
		Worker:    "default",
		TenantIds: []string{commands.DefaultJobTenantID},
	}
	activateJobsRequest := &pb.ActivateJobsRequest{
		Type:              "foo",
		MaxJobsToActivate: 32,
		Timeout:           int64(timeout / time.Millisecond),
		Worker:            "default",
		RequestTimeout:    10000,
		TenantIds:         []string{commands.DefaultJobTenantID},
	}

	stream.EXPECT().Recv().Return(nil, io.EOF).AnyTimes()
	activateJobsStream.EXPECT().Recv().Return(nil, io.EOF).AnyTimes()
	client.EXPECT().StreamActivatedJobs(gomock.Any(), &rpcMsg{msg: request}).AnyTimes().Return(stream, nil)
	client.EXPECT().ActivateJobs(gomock.Any(), &rpcMsg{msg: activateJobsRequest}).Return(activateJobsStream, nil).AnyTimes()

	retryPred := func(ctx context.Context, err error) bool { return err != io.EOF }
	worker := NewJobWorkerBuilder(client, nil, retryPred).
		JobType("foo").
		Handler(func(client JobClient, job entities.Job) {}).
		Timeout(timeout).
		StreamEnabled(true).
		Open()

	closedChan := make(chan bool)
	go func() {
		worker.Close()
		closedChan <- true
		close(closedChan)
	}()

	select {
	case closed := <-closedChan:
		assert.True(t, closed, "Worker should eventually close")
		break
	case <-time.After(10 * time.Second):
		assert.Fail(t, "Failed to close worker after 10 seconds")
		break
	}
}

func TestJobWorkerClose(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	stream := mock_pb.NewMockGateway_ActivateJobsClient(ctrl)
	timeout := 7 * time.Minute
	request := &pb.ActivateJobsRequest{
		Type:              "foo",
		MaxJobsToActivate: 32,
		Timeout:           int64(timeout / time.Millisecond),
		Worker:            "default",
		RequestTimeout:    10000,
		TenantIds:         []string{commands.DefaultJobTenantID},
	}

	stream.EXPECT().Recv().Return(nil, io.EOF).AnyTimes()
	client.EXPECT().ActivateJobs(gomock.Any(), &rpcMsg{msg: request}).Return(stream, nil).AnyTimes()

	retryPred := func(ctx context.Context, err error) bool { return err != io.EOF }
	worker := NewJobWorkerBuilder(client, nil, retryPred).
		JobType("foo").
		Handler(func(client JobClient, job entities.Job) {}).
		Timeout(timeout).
		StreamEnabled(false).
		Open()

	closedChan := make(chan bool)
	go func() {
		worker.Close()
		closedChan <- true
		close(closedChan)
	}()

	select {
	case closed := <-closedChan:
		assert.True(t, closed, "Worker should eventually close")
		break
	case <-time.After(10 * time.Second):
		assert.Fail(t, "Failed to close worker after 10 seconds")
		break
	}
}
