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
	"github.com/zeebe-io/zeebe/clients/go/mock_pb"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"testing"
)

func TestUpdatePayloadCommandWithPayloadFromString(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"

	request := &pb.UpdateWorkflowInstancePayloadRequest{
		ElementInstanceKey: 123,
		Payload:            payload,
	}
	stub := &pb.UpdateWorkflowInstancePayloadResponse{}

	client.EXPECT().UpdateWorkflowInstancePayload(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewUpdatePayloadCommand(client, utils.DefaultTestTimeout)

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
		Payload:            payload,
	}
	stub := &pb.UpdateWorkflowInstancePayloadResponse{}

	client.EXPECT().UpdateWorkflowInstancePayload(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewUpdatePayloadCommand(client, utils.DefaultTestTimeout)

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
		Payload:            payload,
	}
	stub := &pb.UpdateWorkflowInstancePayloadResponse{}

	client.EXPECT().UpdateWorkflowInstancePayload(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewUpdatePayloadCommand(client, utils.DefaultTestTimeout)

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

func TestUpdatePayloadCommandWithPayloadFromObjectOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{}"

	request := &pb.UpdateWorkflowInstancePayloadRequest{
		ElementInstanceKey: 123,
		Payload:            payload,
	}
	stub := &pb.UpdateWorkflowInstancePayloadResponse{}

	client.EXPECT().UpdateWorkflowInstancePayload(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewUpdatePayloadCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.ElementInstanceKey(123).PayloadFromObject(DataType{Foo: ""})
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

func TestUpdatePayloadCommandWithPayloadFromObjectIgnoreOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"\"}"

	request := &pb.UpdateWorkflowInstancePayloadRequest{
		ElementInstanceKey: 123,
		Payload:            payload,
	}
	stub := &pb.UpdateWorkflowInstancePayloadResponse{}

	client.EXPECT().UpdateWorkflowInstancePayload(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewUpdatePayloadCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.ElementInstanceKey(123).PayloadFromObjectIgnoreOmitempty(DataType{Foo: ""})
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
		Payload:            payload,
	}
	stub := &pb.UpdateWorkflowInstancePayloadResponse{}

	client.EXPECT().UpdateWorkflowInstancePayload(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewUpdatePayloadCommand(client, utils.DefaultTestTimeout)

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
