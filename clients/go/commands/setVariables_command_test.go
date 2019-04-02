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

func TestSetVariablesCommandWithVariablesFromString(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.SetVariablesRequest{
		ElementInstanceKey: 123,
		Variables:          variables,
	}
	stub := &pb.SetVariablesResponse{}

	client.EXPECT().SetVariables(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewSetVariablesCommand(client, utils.DefaultTestTimeout)

	variablesCommand, err := command.ElementInstanceKey(123).VariablesFromString(variables)
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	response, err := variablesCommand.Send()

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
	stub := &pb.SetVariablesResponse{}

	client.EXPECT().SetVariables(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewSetVariablesCommand(client, utils.DefaultTestTimeout)

	variablesCommand, err := command.ElementInstanceKey(123).VariablesFromStringer(DataType{Foo: "bar"})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	response, err := variablesCommand.Send()

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
	stub := &pb.SetVariablesResponse{}

	client.EXPECT().SetVariables(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewSetVariablesCommand(client, utils.DefaultTestTimeout)

	variablesCommand, err := command.ElementInstanceKey(123).VariablesFromObject(DataType{Foo: "bar"})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	response, err := variablesCommand.Send()

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
	stub := &pb.SetVariablesResponse{}

	client.EXPECT().SetVariables(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewSetVariablesCommand(client, utils.DefaultTestTimeout)

	variablesCommand, err := command.ElementInstanceKey(123).VariablesFromObject(DataType{Foo: ""})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	response, err := variablesCommand.Send()

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
	stub := &pb.SetVariablesResponse{}

	client.EXPECT().SetVariables(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewSetVariablesCommand(client, utils.DefaultTestTimeout)

	variablesCommand, err := command.ElementInstanceKey(123).VariablesFromObjectIgnoreOmitempty(DataType{Foo: ""})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	response, err := variablesCommand.Send()

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
	stub := &pb.SetVariablesResponse{}

	client.EXPECT().SetVariables(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewSetVariablesCommand(client, utils.DefaultTestTimeout)

	variablesCommand, err := command.ElementInstanceKey(123).VariablesFromMap(variablesMap)
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	response, err := variablesCommand.Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}
