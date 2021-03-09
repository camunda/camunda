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
	"github.com/golang/mock/gomock"
	"github.com/camunda-cloud/zeebe/clients/go/internal/mock_pb"
	"github.com/camunda-cloud/zeebe/clients/go/internal/utils"
	"github.com/camunda-cloud/zeebe/clients/go/pkg/pb"
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

	client.EXPECT().CreateWorkflowInstance(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	response, err := command.WorkflowKey(123).Send(context.Background())

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

	client.EXPECT().CreateWorkflowInstance(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	response, err := command.BPMNProcessId("foo").LatestVersion().Send(context.Background())

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

	client.EXPECT().CreateWorkflowInstance(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	response, err := command.BPMNProcessId("foo").Version(56).Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateWorkflowInstanceCommandWithVariablesFromString(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.CreateWorkflowInstanceRequest{
		WorkflowKey: 123,
		Variables:   variables,
	}
	stub := &pb.CreateWorkflowInstanceResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
	}

	client.EXPECT().CreateWorkflowInstance(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.WorkflowKey(123).VariablesFromString(variables)
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

func TestCreateWorkflowInstanceCommandWithVariablesFromStringer(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.CreateWorkflowInstanceRequest{
		WorkflowKey: 123,
		Variables:   variables,
	}
	stub := &pb.CreateWorkflowInstanceResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
	}

	client.EXPECT().CreateWorkflowInstance(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.WorkflowKey(123).VariablesFromStringer(DataType{Foo: "bar"})
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

func TestCreateWorkflowInstanceCommandWithVariablesFromObject(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.CreateWorkflowInstanceRequest{
		WorkflowKey: 123,
		Variables:   variables,
	}
	stub := &pb.CreateWorkflowInstanceResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
	}

	client.EXPECT().CreateWorkflowInstance(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.WorkflowKey(123).VariablesFromObject(DataType{Foo: "bar"})
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

func TestCreateWorkflowInstanceCommandWithVariablesFromObjectOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{}"

	request := &pb.CreateWorkflowInstanceRequest{
		WorkflowKey: 123,
		Variables:   variables,
	}
	stub := &pb.CreateWorkflowInstanceResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
	}

	client.EXPECT().CreateWorkflowInstance(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.WorkflowKey(123).VariablesFromObject(DataType{Foo: ""})
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

func TestCreateWorkflowInstanceCommandWithVariablesFromObjectIgnoreOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"\"}"

	request := &pb.CreateWorkflowInstanceRequest{
		WorkflowKey: 123,
		Variables:   variables,
	}
	stub := &pb.CreateWorkflowInstanceResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
	}

	client.EXPECT().CreateWorkflowInstance(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.WorkflowKey(123).VariablesFromObjectIgnoreOmitempty(DataType{Foo: ""})
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

func TestCreateWorkflowInstanceCommandWithVariablesFromMap(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"
	variablesMap := make(map[string]interface{})
	variablesMap["foo"] = "bar"

	request := &pb.CreateWorkflowInstanceRequest{
		WorkflowKey: 123,
		Variables:   variables,
	}
	stub := &pb.CreateWorkflowInstanceResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
	}

	client.EXPECT().CreateWorkflowInstance(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.WorkflowKey(123).VariablesFromMap(variablesMap)
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

func TestCreateWorkflowInstanceWithResultCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	request := &pb.CreateWorkflowInstanceWithResultRequest{
		Request: &pb.CreateWorkflowInstanceRequest{
			WorkflowKey: 123,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateWorkflowInstanceWithResultResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
		Variables:           "{}",
	}

	client.EXPECT().CreateWorkflowInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := command.WorkflowKey(123).WithResult().Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateWorkflowInstanceWithResultCommandByBpmnProcessId(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.CreateWorkflowInstanceWithResultRequest{
		Request: &pb.CreateWorkflowInstanceRequest{
			BpmnProcessId: "foo",
			Version:       LatestVersion,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateWorkflowInstanceWithResultResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
		Variables:           "{}",
	}

	client.EXPECT().CreateWorkflowInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

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

func TestCreateWorkflowInstanceWithResultCommandByBpmnProcessIdAndVersion(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.CreateWorkflowInstanceWithResultRequest{
		Request: &pb.CreateWorkflowInstanceRequest{
			BpmnProcessId: "foo",
			Version:       56,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateWorkflowInstanceWithResultResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
		Variables:           "{}",
	}

	client.EXPECT().CreateWorkflowInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

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

func TestCreateWorkflowInstanceWithResultCommandWithVariablesFromString(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	variables := "{\"foo\":\"bar\"}"

	request := &pb.CreateWorkflowInstanceWithResultRequest{
		Request: &pb.CreateWorkflowInstanceRequest{
			WorkflowKey: 123,
			Variables:   variables,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateWorkflowInstanceWithResultResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
		Variables:           variables,
	}

	client.EXPECT().CreateWorkflowInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.WorkflowKey(123).VariablesFromString(variables)
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

func TestCreateWorkflowInstanceWithResultCommandWithVariablesFromStringer(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	variables := "{\"foo\":\"bar\"}"

	request := &pb.CreateWorkflowInstanceWithResultRequest{
		Request: &pb.CreateWorkflowInstanceRequest{
			WorkflowKey: 123,
			Variables:   variables,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateWorkflowInstanceWithResultResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
		Variables:           variables,
	}

	client.EXPECT().CreateWorkflowInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.WorkflowKey(123).VariablesFromStringer(DataType{Foo: "bar"})
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

func TestCreateWorkflowInstanceWithResultCommandWithVariablesFromObject(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	variables := "{\"foo\":\"bar\"}"

	request := &pb.CreateWorkflowInstanceWithResultRequest{
		Request: &pb.CreateWorkflowInstanceRequest{
			WorkflowKey: 123,
			Variables:   variables,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateWorkflowInstanceWithResultResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
		Variables:           variables,
	}

	client.EXPECT().CreateWorkflowInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.WorkflowKey(123).VariablesFromObject(DataType{Foo: "bar"})
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

func TestCreateWorkflowInstanceWithResultCommandWithVariablesFromObjectOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	variables := "{}"

	request := &pb.CreateWorkflowInstanceWithResultRequest{
		Request: &pb.CreateWorkflowInstanceRequest{
			WorkflowKey: 123,
			Variables:   variables,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateWorkflowInstanceWithResultResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
		Variables:           variables,
	}

	client.EXPECT().CreateWorkflowInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.WorkflowKey(123).VariablesFromObject(DataType{Foo: ""})
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

func TestCreateWorkflowInstanceWithResultCommandWithVariablesFromObjectIgnoreOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	variables := "{\"foo\":\"\"}"

	request := &pb.CreateWorkflowInstanceWithResultRequest{
		Request: &pb.CreateWorkflowInstanceRequest{
			WorkflowKey: 123,
			Variables:   variables,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateWorkflowInstanceWithResultResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
		Variables:           variables,
	}

	client.EXPECT().CreateWorkflowInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.WorkflowKey(123).VariablesFromObjectIgnoreOmitempty(DataType{Foo: ""})
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

func TestCreateWorkflowInstanceWithResultCommandWithVariablesFromMap(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"
	variablesMap := make(map[string]interface{})
	variablesMap["foo"] = "bar"

	request := &pb.CreateWorkflowInstanceWithResultRequest{
		Request: &pb.CreateWorkflowInstanceRequest{
			WorkflowKey: 123,
			Variables:   variables,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateWorkflowInstanceWithResultResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
		Variables:           variables,
	}

	client.EXPECT().CreateWorkflowInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.WorkflowKey(123).VariablesFromMap(variablesMap)
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

func TestCreateWorkflowInstanceWithResultAndFetchVariablesCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	request := &pb.CreateWorkflowInstanceWithResultRequest{
		Request: &pb.CreateWorkflowInstanceRequest{
			WorkflowKey: 123,
		},
		RequestTimeout: longPollMillis,
		FetchVariables: []string{"a", "b", "c"},
	}
	stub := &pb.CreateWorkflowInstanceWithResultResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
		Variables:           "{}",
	}

	client.EXPECT().CreateWorkflowInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := command.WorkflowKey(123).WithResult().FetchVariables("a", "b", "c").Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateWorkflowInstanceWithResultAndFetchEmptyVariablesListCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)
	request := &pb.CreateWorkflowInstanceWithResultRequest{
		Request: &pb.CreateWorkflowInstanceRequest{
			WorkflowKey: 123,
		},
		RequestTimeout: longPollMillis,
	}
	stub := &pb.CreateWorkflowInstanceWithResultResponse{
		WorkflowKey:         123,
		BpmnProcessId:       "foo",
		Version:             4545,
		WorkflowInstanceKey: 5632,
		Variables:           "{}",
	}

	client.EXPECT().CreateWorkflowInstanceWithResult(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateInstanceCommand(client, func(context.Context, error) bool { return false })

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := command.WorkflowKey(123).WithResult().FetchVariables().Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}
