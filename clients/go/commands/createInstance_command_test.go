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
	"fmt"
	"github.com/golang/mock/gomock"
	"github.com/zeebe-io/zeebe/clients/go/mock_pb"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"testing"
)

type DataType struct {
	Foo string `json:"foo,omitempty"`
}

func (cmd DataType) String() string {
	return fmt.Sprintf("{\"foo\":\"%s\"}", cmd.Foo)
}

func TestCreateWorkflowInstanceCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.CreateWorkflowInstanceRequest{
		WorkflowKey: 123,
	}
	stub := &pb.CreateWorkflowInstanceResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
	}

	client.EXPECT().CreateWorkflowInstance(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, utils.DefaultTestTimeout)

	response, err := command.WorkflowKey(123).Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateWorkflowInstanceCommandByBpmnProcessId(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.CreateWorkflowInstanceRequest{
		BpmnProcessId: "foo",
		Version:       LatestVersion,
	}
	stub := &pb.CreateWorkflowInstanceResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
	}

	client.EXPECT().CreateWorkflowInstance(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, utils.DefaultTestTimeout)

	response, err := command.BPMNProcessId("foo").LatestVersion().Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateWorkflowInstanceCommandByBpmnProcessIdAndVersion(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.CreateWorkflowInstanceRequest{
		BpmnProcessId: "foo",
		Version:       56,
	}
	stub := &pb.CreateWorkflowInstanceResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
	}

	client.EXPECT().CreateWorkflowInstance(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, utils.DefaultTestTimeout)

	response, err := command.BPMNProcessId("foo").Version(56).Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateWorkflowInstanceCommandWithPayloadFromString(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"

	request := &pb.CreateWorkflowInstanceRequest{
		WorkflowKey: 123,
		Payload:     payload,
	}
	stub := &pb.CreateWorkflowInstanceResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
	}

	client.EXPECT().CreateWorkflowInstance(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.WorkflowKey(123).PayloadFromString(payload)
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

func TestCreateWorkflowInstanceCommandWithPayloadFromStringer(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"

	request := &pb.CreateWorkflowInstanceRequest{
		WorkflowKey: 123,
		Payload:     payload,
	}
	stub := &pb.CreateWorkflowInstanceResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
	}

	client.EXPECT().CreateWorkflowInstance(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.WorkflowKey(123).PayloadFromStringer(DataType{Foo: "bar"})
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

func TestCreateWorkflowInstanceCommandWithPayloadFromObject(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"

	request := &pb.CreateWorkflowInstanceRequest{
		WorkflowKey: 123,
		Payload:     payload,
	}
	stub := &pb.CreateWorkflowInstanceResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
	}

	client.EXPECT().CreateWorkflowInstance(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.WorkflowKey(123).PayloadFromObject(DataType{Foo: "bar"})
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

func TestCreateWorkflowInstanceCommandWithPayloadFromObjectOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{}"

	request := &pb.CreateWorkflowInstanceRequest{
		WorkflowKey: 123,
		Payload:     payload,
	}
	stub := &pb.CreateWorkflowInstanceResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
	}

	client.EXPECT().CreateWorkflowInstance(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.WorkflowKey(123).PayloadFromObject(DataType{Foo: ""})
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

func TestCreateWorkflowInstanceCommandWithPayloadFromObjectIgnoreOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"\"}"

	request := &pb.CreateWorkflowInstanceRequest{
		WorkflowKey: 123,
		Payload:     payload,
	}
	stub := &pb.CreateWorkflowInstanceResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
	}

	client.EXPECT().CreateWorkflowInstance(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.WorkflowKey(123).PayloadFromObjectIgnoreOmitempty(DataType{Foo: ""})
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

func TestCreateWorkflowInstanceCommandWithPayloadFromMap(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"
	payloadMap := make(map[string]interface{})
	payloadMap["foo"] = "bar"

	request := &pb.CreateWorkflowInstanceRequest{
		WorkflowKey: 123,
		Payload:     payload,
	}
	stub := &pb.CreateWorkflowInstanceResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
	}

	client.EXPECT().CreateWorkflowInstance(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.WorkflowKey(123).PayloadFromMap(payloadMap)
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
