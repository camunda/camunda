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
//

package commands

import (
	"context"
	"github.com/camunda/zeebe/clients/go/v8/internal/mock_pb"
	"github.com/camunda/zeebe/clients/go/v8/internal/utils"
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
	"github.com/golang/mock/gomock"
	"testing"
)

func TestBroadcastSignalCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.BroadcastSignalRequest{
		SignalName: "foo",
	}
	stub := &pb.BroadcastSignalResponse{
		Key: 1,
	}

	client.EXPECT().BroadcastSignal(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewBroadcastSignalCommand(client, func(context.Context, error) bool { return false })

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := command.SignalName("foo").Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestBroadcastSignalCommandWithVariablesFromString(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.BroadcastSignalRequest{
		SignalName: "foo",
		Variables:  variables,
	}
	stub := &pb.BroadcastSignalResponse{}

	client.EXPECT().BroadcastSignal(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewBroadcastSignalCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.SignalName("foo").VariablesFromString(variables)
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := variablesCommand.Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestBroadcastSignalCommandWithVariablesFromStringer(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.BroadcastSignalRequest{
		SignalName: "foo",
		Variables:  variables,
	}
	stub := &pb.BroadcastSignalResponse{}

	client.EXPECT().BroadcastSignal(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewBroadcastSignalCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.SignalName("foo").VariablesFromStringer(DataType{Foo: "bar"})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := variablesCommand.Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestBroadcastSignalCommandWithVariablesFromObject(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.BroadcastSignalRequest{
		SignalName: "foo",
		Variables:  variables,
	}
	stub := &pb.BroadcastSignalResponse{}

	client.EXPECT().BroadcastSignal(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewBroadcastSignalCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.SignalName("foo").VariablesFromObject(DataType{Foo: "bar"})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := variablesCommand.Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestBroadcastSignalCommandWithVariablesFromObjectOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{}"

	request := &pb.BroadcastSignalRequest{
		SignalName: "foo",
		Variables:  variables,
	}
	stub := &pb.BroadcastSignalResponse{}

	client.EXPECT().BroadcastSignal(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewBroadcastSignalCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.SignalName("foo").VariablesFromObject(DataType{Foo: ""})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := variablesCommand.Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestBroadcastSignalCommandWithVariablesFromObjectIgnoreOmitEmpty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"\"}"

	request := &pb.BroadcastSignalRequest{
		SignalName: "foo",
		Variables:  variables,
	}
	stub := &pb.BroadcastSignalResponse{}

	client.EXPECT().BroadcastSignal(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewBroadcastSignalCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.SignalName("foo").VariablesFromObjectIgnoreOmitempty(DataType{Foo: ""})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := variablesCommand.Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestBroadcastSignalCommandWithVariablesFromMap(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"
	variablesMap := make(map[string]interface{})
	variablesMap["foo"] = "bar"

	request := &pb.BroadcastSignalRequest{
		SignalName: "foo",
		Variables:  variables,
	}
	stub := &pb.BroadcastSignalResponse{}

	client.EXPECT().BroadcastSignal(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewBroadcastSignalCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.SignalName("foo").VariablesFromMap(variablesMap)
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := variablesCommand.Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}
