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

package commands

import (
	"context"
	"github.com/camunda/zeebe/clients/go/v8/internal/mock_pb"
	"github.com/camunda/zeebe/clients/go/v8/internal/utils"
	"github.com/camunda/zeebe/clients/go/v8/pkg/entities"
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
	"github.com/golang/mock/gomock"
	"io"
	"reflect"
	"testing"
	"time"
)

const (
	longPollMillis = int64(float64(utils.DefaultTestTimeoutInMs) * (1.0 - longPollingOffsetPercent))
)

func TestActivateJobsCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	stream := mock_pb.NewMockGateway_ActivateJobsClient(ctrl)

	request := &pb.ActivateJobsRequest{
		Type:              "foo",
		MaxJobsToActivate: 5,
		Timeout:           DefaultJobTimeoutInMs,
		Worker:            DefaultJobWorkerName,
		RequestTimeout:    longPollMillis,
	}

	response1 := &pb.ActivateJobsResponse{
		Jobs: []*pb.ActivatedJob{
			{
				Key:                      123,
				Type:                     "foo",
				Retries:                  3,
				Deadline:                 123123,
				Worker:                   DefaultJobWorkerName,
				ElementInstanceKey:       123,
				ProcessDefinitionKey:     124,
				BpmnProcessId:            "fooProcess",
				ProcessInstanceKey:       1233,
				ElementId:                "foobar",
				ProcessDefinitionVersion: 12345,
				CustomHeaders:            "{\"foo\": \"bar\"}",
				Variables:                "{\"foo\": \"bar\"}",
			},
		},
	}
	response2 := &pb.ActivateJobsResponse{
		Jobs: []*pb.ActivatedJob{},
	}
	response3 := &pb.ActivateJobsResponse{
		Jobs: []*pb.ActivatedJob{
			{
				Key:                      123,
				Type:                     "foo",
				Retries:                  3,
				Deadline:                 123123,
				Worker:                   DefaultJobWorkerName,
				ElementInstanceKey:       123,
				ProcessDefinitionKey:     124,
				BpmnProcessId:            "fooProcess",
				ProcessInstanceKey:       1233,
				ElementId:                "foobar",
				ProcessDefinitionVersion: 12345,
				CustomHeaders:            "{\"foo\": \"bar\"}",
				Variables:                "{\"foo\": \"bar\"}",
			},
			{
				Key:           123,
				Type:          "foo",
				Retries:       3,
				Deadline:      123123,
				Worker:        DefaultJobWorkerName,
				CustomHeaders: "{}",
				Variables:     "{}",
			},
		},
	}

	var expectedJobs []entities.Job
	for _, job := range response1.Jobs {
		expectedJobs = append(expectedJobs, entities.Job{ActivatedJob: job})
	}
	for _, job := range response2.Jobs {
		expectedJobs = append(expectedJobs, entities.Job{ActivatedJob: job})
	}
	for _, job := range response3.Jobs {
		expectedJobs = append(expectedJobs, entities.Job{ActivatedJob: job})
	}

	gomock.InOrder(
		stream.EXPECT().Recv().Return(response1, nil),
		stream.EXPECT().Recv().Return(response2, nil),
		stream.EXPECT().Recv().Return(response3, nil),
		stream.EXPECT().Recv().Return(nil, io.EOF),
	)

	client.EXPECT().ActivateJobs(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stream, nil)

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	jobs, err := NewActivateJobsCommand(client, func(context.Context, error) bool {
		return false
	}).JobType("foo").MaxJobsToActivate(5).Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if len(jobs) != len(expectedJobs) {
		t.Error("Failed to receive all jobs: ", jobs, expectedJobs)
	}

	for i, job := range jobs {
		if !reflect.DeepEqual(job, expectedJobs[i]) {
			t.Error("Failed to receive job: ", job, expectedJobs[i])
		}
	}
}

func TestActivateJobsCommandWithTimeout(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	stream := mock_pb.NewMockGateway_ActivateJobsClient(ctrl)

	request := &pb.ActivateJobsRequest{
		Type:              "foo",
		MaxJobsToActivate: 5,
		Timeout:           60 * 1000,
		Worker:            DefaultJobWorkerName,
		RequestTimeout:    longPollMillis,
	}

	stream.EXPECT().Recv().Return(nil, io.EOF)
	client.EXPECT().ActivateJobs(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stream, nil)

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	jobs, err := NewActivateJobsCommand(client, func(context.Context, error) bool {
		return false
	}).JobType("foo").MaxJobsToActivate(5).Timeout(1 * time.Minute).Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if len(jobs) != 0 {
		t.Errorf("Failed to receive response")
	}
}

func TestActivateJobsCommandWithWorkerName(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	stream := mock_pb.NewMockGateway_ActivateJobsClient(ctrl)

	request := &pb.ActivateJobsRequest{
		Type:              "foo",
		MaxJobsToActivate: 5,
		Timeout:           DefaultJobTimeoutInMs,
		Worker:            "bar",
		RequestTimeout:    longPollMillis,
	}

	stream.EXPECT().Recv().Return(nil, io.EOF)
	client.EXPECT().ActivateJobs(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stream, nil)

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	jobs, err := NewActivateJobsCommand(client, func(context.Context, error) bool {
		return false
	}).JobType("foo").MaxJobsToActivate(5).WorkerName("bar").Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if len(jobs) != 0 {
		t.Errorf("Failed to receive response")
	}
}

func TestActivateJobsCommandWithFetchVariables(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	stream := mock_pb.NewMockGateway_ActivateJobsClient(ctrl)

	fetchVariables := []string{"foo", "bar", "baz"}

	request := &pb.ActivateJobsRequest{
		Type:              "foo",
		MaxJobsToActivate: 5,
		Worker:            DefaultJobWorkerName,
		Timeout:           DefaultJobTimeoutInMs,
		FetchVariable:     fetchVariables,
		RequestTimeout:    longPollMillis,
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	stream.EXPECT().Recv().Return(nil, io.EOF)
	client.EXPECT().ActivateJobs(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stream, nil)

	jobs, err := NewActivateJobsCommand(client, func(context.Context, error) bool {
		return false
	}).JobType("foo").MaxJobsToActivate(5).FetchVariables(fetchVariables...).Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if len(jobs) != 0 {
		t.Errorf("Failed to receive response")
	}
}
