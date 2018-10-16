package commands

import (
	"github.com/golang/mock/gomock"
	"github.com/zeebe-io/zeebe/clients/go/mock_pb"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"testing"
)

func TestUpdateJobRetriesCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.UpdateJobRetriesRequest{
		JobKey:  123,
		Retries: utils.DefaultRetries,
	}
	stub := &pb.UpdateJobRetriesResponse{}

	client.EXPECT().UpdateJobRetries(gomock.Any(), &rpcMsg{msg: request}).Return(stub, nil)

	command := NewUpdateJobRetriesCommand(client)

	response, err := command.JobKey(123).Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestUpdateJobRetriesCommandWithRetries(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.UpdateJobRetriesRequest{
		JobKey:  123,
		Retries: 23,
	}
	stub := &pb.UpdateJobRetriesResponse{}

	client.EXPECT().UpdateJobRetries(gomock.Any(), &rpcMsg{msg: request}).Return(stub, nil)

	command := NewUpdateJobRetriesCommand(client)

	response, err := command.JobKey(123).Retries(23).Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}
