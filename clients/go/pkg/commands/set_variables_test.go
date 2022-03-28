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
	"github.com/camunda/zeebe/clients/go/internal/mock_pb"
	"github.com/camunda/zeebe/clients/go/internal/utils"
	"github.com/camunda/zeebe/clients/go/pkg/pb"
	"github.com/golang/mock/gomock"
	"testing"
)

func TestSetVariablesCommandWithVariablesFromString(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.SetVariablesRequest{
		ElementInstanceKey: 123,
		Variables:          variables,
	}
	stub := &pb.SetVariablesResponse{
		Key: 523,
	}

	client.EXPECT().SetVariables(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewSetVariablesCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.ElementInstanceKey(123).VariablesFromString(variables)
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

func TestSetVariablesCommandWithVariablesFromStringer(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.SetVariablesRequest{
		ElementInstanceKey: 123,
		Variables:          variables,
	}
	stub := &pb.SetVariablesResponse{
		Key: 523,
	}

	client.EXPECT().SetVariables(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewSetVariablesCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.ElementInstanceKey(123).VariablesFromStringer(DataType{Foo: "bar"})
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

func TestSetVariablesCommandWithVariablesFromObject(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.SetVariablesRequest{
		ElementInstanceKey: 123,
		Variables:          variables,
	}
	stub := &pb.SetVariablesResponse{
		Key: 523,
	}

	client.EXPECT().SetVariables(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewSetVariablesCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.ElementInstanceKey(123).VariablesFromObject(DataType{Foo: "bar"})
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

func TestSetVariablesCommandWithVariablesFromObjectOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{}"

	request := &pb.SetVariablesRequest{
		ElementInstanceKey: 123,
		Variables:          variables,
	}
	stub := &pb.SetVariablesResponse{
		Key: 523,
	}

	client.EXPECT().SetVariables(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewSetVariablesCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.ElementInstanceKey(123).VariablesFromObject(DataType{Foo: ""})
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

func TestSetVariablesCommandWithVariablesFromObjectIgnoreOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"\"}"

	request := &pb.SetVariablesRequest{
		ElementInstanceKey: 123,
		Variables:          variables,
	}
	stub := &pb.SetVariablesResponse{
		Key: 523,
	}

	client.EXPECT().SetVariables(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewSetVariablesCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.ElementInstanceKey(123).VariablesFromObjectIgnoreOmitempty(DataType{Foo: ""})
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

func TestSetVariablesCommandWithVariablesFromMap(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"
	variablesMap := make(map[string]interface{})
	variablesMap["foo"] = "bar"

	request := &pb.SetVariablesRequest{
		ElementInstanceKey: 123,
		Variables:          variables,
	}
	stub := &pb.SetVariablesResponse{
		Key: 523,
	}

	client.EXPECT().SetVariables(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewSetVariablesCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.ElementInstanceKey(123).VariablesFromMap(variablesMap)
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
