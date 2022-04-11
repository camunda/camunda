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
	"context"
	"fmt"
	"github.com/camunda/zeebe/clients/go/v8/internal/mock_pb"
	"github.com/camunda/zeebe/clients/go/v8/internal/utils"
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
	"github.com/golang/mock/gomock"
	"testing"
)

type DataType struct {
	Foo string `json:"foo,omitempty"`
}

func (cmd DataType) String() string {
	return fmt.Sprintf("{\"foo\":\"%s\"}", cmd.Foo)
}

func TestCreateProcessInstanceCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.CreateProcessInstanceRequest{
		ProcessDefinitionKey: 123,
	}
	stub := &pb.CreateProcessInstanceResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
	}

	client.EXPECT().CreateProcessInstance(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	response, err := command.ProcessDefinitionKey(123).Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceCommandByBpmnProcessId(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.CreateProcessInstanceRequest{
		BpmnProcessId: "foo",
		Version:       LatestVersion,
	}
	stub := &pb.CreateProcessInstanceResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
	}

	client.EXPECT().CreateProcessInstance(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	response, err := command.BPMNProcessId("foo").LatestVersion().Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceCommandByBpmnProcessIdAndVersion(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.CreateProcessInstanceRequest{
		BpmnProcessId: "foo",
		Version:       56,
	}
	stub := &pb.CreateProcessInstanceResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
	}

	client.EXPECT().CreateProcessInstance(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	response, err := command.BPMNProcessId("foo").Version(56).Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceCommandWithVariablesFromString(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.CreateProcessInstanceRequest{
		ProcessDefinitionKey: 123,
		Variables:            variables,
	}
	stub := &pb.CreateProcessInstanceResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
	}

	client.EXPECT().CreateProcessInstance(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.ProcessDefinitionKey(123).VariablesFromString(variables)
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	response, err := variablesCommand.Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceCommandWithVariablesFromStringer(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.CreateProcessInstanceRequest{
		ProcessDefinitionKey: 123,
		Variables:            variables,
	}
	stub := &pb.CreateProcessInstanceResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
	}

	client.EXPECT().CreateProcessInstance(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.ProcessDefinitionKey(123).VariablesFromStringer(DataType{Foo: "bar"})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	response, err := variablesCommand.Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceCommandWithVariablesFromObject(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.CreateProcessInstanceRequest{
		ProcessDefinitionKey: 123,
		Variables:            variables,
	}
	stub := &pb.CreateProcessInstanceResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
	}

	client.EXPECT().CreateProcessInstance(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.ProcessDefinitionKey(123).VariablesFromObject(DataType{Foo: "bar"})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	response, err := variablesCommand.Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceCommandWithVariablesFromObjectOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{}"

	request := &pb.CreateProcessInstanceRequest{
		ProcessDefinitionKey: 123,
		Variables:            variables,
	}
	stub := &pb.CreateProcessInstanceResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
	}

	client.EXPECT().CreateProcessInstance(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.ProcessDefinitionKey(123).VariablesFromObject(DataType{Foo: ""})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	response, err := variablesCommand.Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceCommandWithVariablesFromObjectIgnoreOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"\"}"

	request := &pb.CreateProcessInstanceRequest{
		ProcessDefinitionKey: 123,
		Variables:            variables,
	}
	stub := &pb.CreateProcessInstanceResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
	}

	client.EXPECT().CreateProcessInstance(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.ProcessDefinitionKey(123).VariablesFromObjectIgnoreOmitempty(DataType{Foo: ""})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	response, err := variablesCommand.Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceCommandWithVariablesFromMap(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"
	variablesMap := make(map[string]interface{})
	variablesMap["foo"] = "bar"

	request := &pb.CreateProcessInstanceRequest{
		ProcessDefinitionKey: 123,
		Variables:            variables,
	}
	stub := &pb.CreateProcessInstanceResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
	}

	client.EXPECT().CreateProcessInstance(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.ProcessDefinitionKey(123).VariablesFromMap(variablesMap)
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	response, err := variablesCommand.Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceWithResultCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	request := &pb.CreateProcessInstanceWithResultRequest{
		Request: &pb.CreateProcessInstanceRequest{
			ProcessDefinitionKey: 123,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateProcessInstanceWithResultResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
		Variables:            "{}",
	}

	client.EXPECT().CreateProcessInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := command.ProcessDefinitionKey(123).WithResult().Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceWithResultCommandByBpmnProcessId(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.CreateProcessInstanceWithResultRequest{
		Request: &pb.CreateProcessInstanceRequest{
			BpmnProcessId: "foo",
			Version:       LatestVersion,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateProcessInstanceWithResultResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
		Variables:            "{}",
	}

	client.EXPECT().CreateProcessInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := command.BPMNProcessId("foo").LatestVersion().WithResult().Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceWithResultCommandByBpmnProcessIdAndVersion(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.CreateProcessInstanceWithResultRequest{
		Request: &pb.CreateProcessInstanceRequest{
			BpmnProcessId: "foo",
			Version:       56,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateProcessInstanceWithResultResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
		Variables:            "{}",
	}

	client.EXPECT().CreateProcessInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := command.BPMNProcessId("foo").Version(56).WithResult().Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceWithResultCommandWithVariablesFromString(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	variables := "{\"foo\":\"bar\"}"

	request := &pb.CreateProcessInstanceWithResultRequest{
		Request: &pb.CreateProcessInstanceRequest{
			ProcessDefinitionKey: 123,
			Variables:            variables,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateProcessInstanceWithResultResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
		Variables:            variables,
	}

	client.EXPECT().CreateProcessInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.ProcessDefinitionKey(123).VariablesFromString(variables)
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := variablesCommand.WithResult().Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceWithResultCommandWithVariablesFromStringer(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	variables := "{\"foo\":\"bar\"}"

	request := &pb.CreateProcessInstanceWithResultRequest{
		Request: &pb.CreateProcessInstanceRequest{
			ProcessDefinitionKey: 123,
			Variables:            variables,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateProcessInstanceWithResultResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
		Variables:            variables,
	}

	client.EXPECT().CreateProcessInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.ProcessDefinitionKey(123).VariablesFromStringer(DataType{Foo: "bar"})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := variablesCommand.WithResult().Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceWithResultCommandWithVariablesFromObject(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	variables := "{\"foo\":\"bar\"}"

	request := &pb.CreateProcessInstanceWithResultRequest{
		Request: &pb.CreateProcessInstanceRequest{
			ProcessDefinitionKey: 123,
			Variables:            variables,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateProcessInstanceWithResultResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
		Variables:            variables,
	}

	client.EXPECT().CreateProcessInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.ProcessDefinitionKey(123).VariablesFromObject(DataType{Foo: "bar"})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := variablesCommand.WithResult().Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceWithResultCommandWithVariablesFromObjectOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	variables := "{}"

	request := &pb.CreateProcessInstanceWithResultRequest{
		Request: &pb.CreateProcessInstanceRequest{
			ProcessDefinitionKey: 123,
			Variables:            variables,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateProcessInstanceWithResultResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
		Variables:            variables,
	}

	client.EXPECT().CreateProcessInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.ProcessDefinitionKey(123).VariablesFromObject(DataType{Foo: ""})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := variablesCommand.WithResult().Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceWithResultCommandWithVariablesFromObjectIgnoreOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	variables := "{\"foo\":\"\"}"

	request := &pb.CreateProcessInstanceWithResultRequest{
		Request: &pb.CreateProcessInstanceRequest{
			ProcessDefinitionKey: 123,
			Variables:            variables,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateProcessInstanceWithResultResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
		Variables:            variables,
	}

	client.EXPECT().CreateProcessInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.ProcessDefinitionKey(123).VariablesFromObjectIgnoreOmitempty(DataType{Foo: ""})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := variablesCommand.WithResult().Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceWithResultCommandWithVariablesFromMap(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"
	variablesMap := make(map[string]interface{})
	variablesMap["foo"] = "bar"

	request := &pb.CreateProcessInstanceWithResultRequest{
		Request: &pb.CreateProcessInstanceRequest{
			ProcessDefinitionKey: 123,
			Variables:            variables,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateProcessInstanceWithResultResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
		Variables:            variables,
	}

	client.EXPECT().CreateProcessInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.ProcessDefinitionKey(123).VariablesFromMap(variablesMap)
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := variablesCommand.WithResult().Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceWithResultAndFetchVariablesCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	request := &pb.CreateProcessInstanceWithResultRequest{
		Request: &pb.CreateProcessInstanceRequest{
			ProcessDefinitionKey: 123,
		},
		RequestTimeout: longPollMillis,
		FetchVariables: []string{"a", "b", "c"},
	}
	stub := &pb.CreateProcessInstanceWithResultResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
		Variables:            "{}",
	}

	client.EXPECT().CreateProcessInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := command.ProcessDefinitionKey(123).WithResult().FetchVariables("a", "b", "c").Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateProcessInstanceWithResultAndFetchEmptyVariablesListCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	request := &pb.CreateProcessInstanceWithResultRequest{
		Request: &pb.CreateProcessInstanceRequest{
			ProcessDefinitionKey: 123,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateProcessInstanceWithResultResponse{
		ProcessDefinitionKey: 123,
		BpmnProcessId:        "foo",
		Version:              4545,
		ProcessInstanceKey:   5632,
		Variables:            "{}",
	}

	client.EXPECT().CreateProcessInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := command.ProcessDefinitionKey(123).WithResult().FetchVariables().Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}
