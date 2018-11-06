package commands

import (
	"github.com/golang/mock/gomock"
	"github.com/zeebe-io/zeebe/clients/go/mock_pb"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"testing"
)

func TestGetWorkflowCommandByWorkflowKey(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.GetWorkflowRequest{
		WorkflowKey: 123,
	}
	stub := &pb.GetWorkflowResponse{
		WorkflowKey:   123,
		Version:       1,
		BpmnProcessId: "testProcess",
		ResourceName:  "process.bpmn",
		BpmnXml:       "<xml/>",
	}

	client.EXPECT().GetWorkflow(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewGetWorkflowCommand(client, utils.DefaultTestTimeout)

	response, err := command.WorkflowKey(123).Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestGetWorkflowCommandByBpmnProcessId(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.GetWorkflowRequest{
		BpmnProcessId: "testProcess",
		Version:       LatestVersion,
	}
	stub := &pb.GetWorkflowResponse{
		WorkflowKey:   123,
		Version:       1,
		BpmnProcessId: "testProcess",
		ResourceName:  "process.bpmn",
		BpmnXml:       "<xml/>",
	}

	client.EXPECT().GetWorkflow(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewGetWorkflowCommand(client, utils.DefaultTestTimeout)

	response, err := command.BpmnProcessId("testProcess").LatestVersion().Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestGetWorkflowCommandByBpmnProcessIdAndVersion(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.GetWorkflowRequest{
		BpmnProcessId: "testProcess",
		Version:       32,
	}
	stub := &pb.GetWorkflowResponse{
		WorkflowKey:   123,
		Version:       32,
		BpmnProcessId: "testProcess",
		ResourceName:  "process.bpmn",
		BpmnXml:       "<xml/>",
	}

	client.EXPECT().GetWorkflow(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewGetWorkflowCommand(client, utils.DefaultTestTimeout)

	response, err := command.BpmnProcessId("testProcess").Version(32).Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}
