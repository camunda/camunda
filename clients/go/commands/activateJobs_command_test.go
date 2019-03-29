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
	"github.com/golang/mock/gomock"
	"github.com/zeebe-io/zeebe/clients/go/entities"
	"github.com/zeebe-io/zeebe/clients/go/mock_pb"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"io"
	"reflect"
	"testing"
	"time"
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
	}

	response1 := &pb.ActivateJobsResponse{
		Jobs: []*pb.ActivatedJob{
			{
				Key:      123,
				Type:     "foo",
				Retries:  3,
				Deadline: 123123,
				Worker:   DefaultJobWorkerName,
				JobHeaders: &pb.JobHeaders{
					ElementInstanceKey:        123,
					WorkflowKey:               124,
					BpmnProcessId:             "fooProcess",
					WorkflowInstanceKey:       1233,
					ElementId:                 "foobar",
					WorkflowDefinitionVersion: 12345,
				},
				CustomHeaders: "{\"foo\": \"bar\"}",
				Variables:     "{\"foo\": \"bar\"}",
			},
		},
	}
	response2 := &pb.ActivateJobsResponse{
		Jobs: []*pb.ActivatedJob{},
	}
	response3 := &pb.ActivateJobsResponse{
		Jobs: []*pb.ActivatedJob{
			{
				Key:      123,
				Type:     "foo",
				Retries:  3,
				Deadline: 123123,
				Worker:   DefaultJobWorkerName,
				JobHeaders: &pb.JobHeaders{
					ElementInstanceKey:        123,
					WorkflowKey:               124,
					BpmnProcessId:             "fooProcess",
					WorkflowInstanceKey:       1233,
					ElementId:                 "foobar",
					WorkflowDefinitionVersion: 12345,
				},
				CustomHeaders: "{\"foo\": \"bar\"}",
				Variables:     "{\"foo\": \"bar\"}",
			},
			{
				Key:           123,
				Type:          "foo",
				Retries:       3,
				Deadline:      123123,
				Worker:        DefaultJobWorkerName,
				JobHeaders:    &pb.JobHeaders{},
				CustomHeaders: "{}",
				Variables:     "{}",
			},
		},
	}

	var expectedJobs []entities.Job
	for _, job := range response1.Jobs {
		expectedJobs = append(expectedJobs, entities.Job{*job})
	}
	for _, job := range response2.Jobs {
		expectedJobs = append(expectedJobs, entities.Job{*job})
	}
	for _, job := range response3.Jobs {
		expectedJobs = append(expectedJobs, entities.Job{*job})
	}

	gomock.InOrder(
		stream.EXPECT().Recv().Return(response1, nil),
		stream.EXPECT().Recv().Return(response2, nil),
		stream.EXPECT().Recv().Return(response3, nil),
		stream.EXPECT().Recv().Return(nil, io.EOF),
	)

	client.EXPECT().ActivateJobs(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stream, nil)

	jobs, err := NewActivateJobsCommand(client, utils.DefaultTestTimeout).JobType("foo").MaxJobsToActivate(5).Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if len(jobs) != len(expectedJobs) {
		t.Error("Failed to receive all jobs: ", jobs, expectedJobs)
	}

	for i := range jobs {
		if !reflect.DeepEqual(jobs[i], expectedJobs[i]) {
			t.Error("Failed to receive job: ", jobs[i], expectedJobs[i])
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
	}

	stream.EXPECT().Recv().Return(nil, io.EOF)
	client.EXPECT().ActivateJobs(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stream, nil)

	jobs, err := NewActivateJobsCommand(client, utils.DefaultTestTimeout).JobType("foo").MaxJobsToActivate(5).Timeout(1 * time.Minute).Send()

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
	}

	stream.EXPECT().Recv().Return(nil, io.EOF)
	client.EXPECT().ActivateJobs(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stream, nil)

	jobs, err := NewActivateJobsCommand(client, utils.DefaultTestTimeout).JobType("foo").MaxJobsToActivate(5).WorkerName("bar").Send()

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
	}

	stream.EXPECT().Recv().Return(nil, io.EOF)
	client.EXPECT().ActivateJobs(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stream, nil)

	jobs, err := NewActivateJobsCommand(client, utils.DefaultTestTimeout).JobType("foo").MaxJobsToActivate(5).FetchVariables(fetchVariables...).Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if len(jobs) != 0 {
		t.Errorf("Failed to receive response")
	}
}
