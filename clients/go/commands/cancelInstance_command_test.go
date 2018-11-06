package commands

import (
	"github.com/golang/mock/gomock"
	"github.com/zeebe-io/zeebe/clients/go/mock_pb"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"testing"
)

func TestCancelWorkflowInstanceCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.CancelWorkflowInstanceRequest{
		WorkflowInstanceKey: 123,
	}
	stub := &pb.CancelWorkflowInstanceResponse{}

	client.EXPECT().CancelWorkflowInstance(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCancelInstanceCommand(client, utils.DefaultTestTimeout)

	response, err := command.WorkflowInstanceKey(123).Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}
