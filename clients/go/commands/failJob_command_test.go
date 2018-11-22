package commands

import (
	"github.com/golang/mock/gomock"
	"github.com/zeebe-io/zeebe/clients/go/mock_pb"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"testing"
)

func TestFailJobCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.FailJobRequest{
		JobKey:  123,
		Retries: 12,
	}
	stub := &pb.FailJobResponse{}

	client.EXPECT().FailJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewFailJobCommand(client, utils.DefaultTestTimeout)

	response, err := command.JobKey(123).Retries(12).Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestFailJobCommand_ErrorMessage(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	errorMessage := "something went wrong"

	request := &pb.FailJobRequest{
		JobKey:       123,
		Retries:      12,
		ErrorMessage: errorMessage,
	}
	stub := &pb.FailJobResponse{}

	client.EXPECT().FailJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewFailJobCommand(client, utils.DefaultTestTimeout)

	response, err := command.JobKey(123).Retries(12).ErrorMessage(errorMessage).Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}
