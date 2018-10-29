package commands

import (
	"github.com/golang/mock/gomock"
	"github.com/zeebe-io/zeebe/clients/go/mock_pb"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"io"
	"testing"
	"time"
)

func TestActivateJobsCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	stream := mock_pb.NewMockGateway_ActivateJobsClient(ctrl)

	request := &pb.ActivateJobsRequest{
		Type:    "foo",
		Amount:  5,
		Timeout: utils.DefaultJobTimeoutInMs,
		Worker:  utils.DefaultJobWorkerName,
	}

	response1 := &pb.ActivateJobsResponse{
		Jobs: []*pb.ActivatedJob{
			{
				Key:      123,
				Type:     "foo",
				Retries:  3,
				Deadline: 123123,
				Worker:   utils.DefaultJobWorkerName,
				JobHeaders: &pb.JobHeaders{
					ElementInstanceKey:       123,
					WorkflowKey:               124,
					BpmnProcessId:             "fooProcess",
					WorkflowInstanceKey:       1233,
					ElementId:                "foobar",
					WorkflowDefinitionVersion: 12345,
				},
				CustomHeaders: "{\"foo\": \"bar\"}",
				Payload:       "{\"foo\": \"bar\"}",
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
				Worker:   utils.DefaultJobWorkerName,
				JobHeaders: &pb.JobHeaders{
					ElementInstanceKey:       123,
					WorkflowKey:               124,
					BpmnProcessId:             "fooProcess",
					WorkflowInstanceKey:       1233,
					ElementId:                "foobar",
					WorkflowDefinitionVersion: 12345,
				},
				CustomHeaders: "{\"foo\": \"bar\"}",
				Payload:       "{\"foo\": \"bar\"}",
			},
			{
				Key:           123,
				Type:          "foo",
				Retries:       3,
				Deadline:      123123,
				Worker:        utils.DefaultJobWorkerName,
				JobHeaders:    &pb.JobHeaders{},
				CustomHeaders: "{}",
				Payload:       "{}",
			},
		},
	}

	var expectedJobs []*pb.ActivatedJob
	expectedJobs = append(expectedJobs, response1.Jobs...)
	expectedJobs = append(expectedJobs, response2.Jobs...)
	expectedJobs = append(expectedJobs, response3.Jobs...)

	gomock.InOrder(
		stream.EXPECT().Recv().Return(response1, nil),
		stream.EXPECT().Recv().Return(response2, nil),
		stream.EXPECT().Recv().Return(response3, nil),
		stream.EXPECT().Recv().Return(nil, io.EOF),
	)

	client.EXPECT().ActivateJobs(gomock.Any(), &rpcMsg{msg: request}).Return(stream, nil)

	jobs, err := NewActivateJobsCommand(client).JobType("foo").Amount(5).Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if len(jobs) != len(expectedJobs) {
		t.Error("Failed to receive all jobs: ", jobs, expectedJobs)
	}

	for i := range jobs {
		if jobs[i] != expectedJobs[i] {
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
		Type:    "foo",
		Amount:  5,
		Timeout: 60 * 1000,
		Worker:  utils.DefaultJobWorkerName,
	}

	stream.EXPECT().Recv().Return(nil, io.EOF)
	client.EXPECT().ActivateJobs(gomock.Any(), &rpcMsg{msg: request}).Return(stream, nil)

	jobs, err := NewActivateJobsCommand(client).JobType("foo").Amount(5).Timeout(1 * time.Minute).Send()

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
		Type:    "foo",
		Amount:  5,
		Timeout: utils.DefaultJobTimeoutInMs,
		Worker:  "bar",
	}

	stream.EXPECT().Recv().Return(nil, io.EOF)
	client.EXPECT().ActivateJobs(gomock.Any(), &rpcMsg{msg: request}).Return(stream, nil)

	jobs, err := NewActivateJobsCommand(client).JobType("foo").Amount(5).WorkerName("bar").Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if len(jobs) != 0 {
		t.Errorf("Failed to receive response")
	}
}
