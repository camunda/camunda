package commands

import (
	"github.com/golang/mock/gomock"
	"github.com/zeebe-io/zeebe/clients/go/mock_pb"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"testing"
)

func TestUpdatePayloadCommandWithPayloadFromString(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"

	request := &pb.UpdateWorkflowInstancePayloadRequest{
		ElementInstanceKey: 123,
		Payload:             payload,
	}
	stub := &pb.UpdateWorkflowInstancePayloadResponse{}

	client.EXPECT().UpdateWorkflowInstancePayload(gomock.Any(), &rpcMsg{msg: request}).Return(stub, nil)

	command := NewUpdatePayloadCommand(client)

	payloadCommand, err := command.ElementInstanceKey(123).PayloadFromString(payload)
	if err != nil {
		t.Error("Failed to set payload: ", err)
	}

	response, err := payloadCommand.Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestUpdatePayloadCommandWithPayloadFromStringer(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"

	request := &pb.UpdateWorkflowInstancePayloadRequest{
		ElementInstanceKey: 123,
		Payload:             payload,
	}
	stub := &pb.UpdateWorkflowInstancePayloadResponse{}

	client.EXPECT().UpdateWorkflowInstancePayload(gomock.Any(), &rpcMsg{msg: request}).Return(stub, nil)

	command := NewUpdatePayloadCommand(client)

	payloadCommand, err := command.ElementInstanceKey(123).PayloadFromStringer(DataType{Foo: "bar"})
	if err != nil {
		t.Error("Failed to set payload: ", err)
	}

	response, err := payloadCommand.Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestUpdatePayloadCommandWithPayloadFromObject(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"

	request := &pb.UpdateWorkflowInstancePayloadRequest{
		ElementInstanceKey: 123,
		Payload:             payload,
	}
	stub := &pb.UpdateWorkflowInstancePayloadResponse{}

	client.EXPECT().UpdateWorkflowInstancePayload(gomock.Any(), &rpcMsg{msg: request}).Return(stub, nil)

	command := NewUpdatePayloadCommand(client)

	payloadCommand, err := command.ElementInstanceKey(123).PayloadFromObject(DataType{Foo: "bar"})
	if err != nil {
		t.Error("Failed to set payload: ", err)
	}

	response, err := payloadCommand.Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestUpdatePayloadCommandWithPayloadFromMap(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"
	payloadMap := make(map[string]interface{})
	payloadMap["foo"] = "bar"

	request := &pb.UpdateWorkflowInstancePayloadRequest{
		ElementInstanceKey: 123,
		Payload:             payload,
	}
	stub := &pb.UpdateWorkflowInstancePayloadResponse{}

	client.EXPECT().UpdateWorkflowInstancePayload(gomock.Any(), &rpcMsg{msg: request}).Return(stub, nil)

	command := NewUpdatePayloadCommand(client)

	payloadCommand, err := command.ElementInstanceKey(123).PayloadFromMap(payloadMap)
	if err != nil {
		t.Error("Failed to set payload: ", err)
	}

	response, err := payloadCommand.Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}
