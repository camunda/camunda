package commands

import (
	"github.com/golang/mock/gomock"
	"github.com/zeebe-io/zeebe/clients/go/mock_pb"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"testing"
)

func TestCompleteJobCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.CompleteJobRequest{
		JobKey: 123,
	}
	stub := &pb.CompleteJobResponse{}

	client.EXPECT().CompleteJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	response, err := NewCompleteJobCommand(client, utils.DefaultTestTimeout).JobKey(123).Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCompleteJobCommandWithPayloadFromString(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"

	request := &pb.CompleteJobRequest{
		JobKey:  123,
		Payload: payload,
	}
	stub := &pb.CompleteJobResponse{}

	client.EXPECT().CompleteJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCompleteJobCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.JobKey(123).PayloadFromString(payload)
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

func TestCompleteJobCommandWithPayloadFromStringer(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"

	request := &pb.CompleteJobRequest{
		JobKey:  123,
		Payload: payload,
	}
	stub := &pb.CompleteJobResponse{}

	client.EXPECT().CompleteJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCompleteJobCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.JobKey(123).PayloadFromStringer(DataType{Foo: "bar"})
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

func TestCompleteJobCommandWithPayloadFromObject(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"

	request := &pb.CompleteJobRequest{
		JobKey:  123,
		Payload: payload,
	}
	stub := &pb.CompleteJobResponse{}

	client.EXPECT().CompleteJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCompleteJobCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.JobKey(123).PayloadFromObject(DataType{Foo: "bar"})
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

func TestCompleteJobCommandWithPayloadFromMap(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"
	payloadMap := make(map[string]interface{})
	payloadMap["foo"] = "bar"

	request := &pb.CompleteJobRequest{
		JobKey:  123,
		Payload: payload,
	}
	stub := &pb.CompleteJobResponse{}

	client.EXPECT().CompleteJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCompleteJobCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.JobKey(123).PayloadFromMap(payloadMap)
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
