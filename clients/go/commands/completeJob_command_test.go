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

func TestCompleteJobCommandWithVariablesFromString(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.CompleteJobRequest{
		JobKey:    123,
		Variables: variables,
	}
	stub := &pb.CompleteJobResponse{}

	client.EXPECT().CompleteJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCompleteJobCommand(client, utils.DefaultTestTimeout)

	variablesCommand, err := command.JobKey(123).VariablesFromString(variables)
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

func TestCompleteJobCommandWithVariablesFromStringer(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.CompleteJobRequest{
		JobKey:    123,
		Variables: variables,
	}
	stub := &pb.CompleteJobResponse{}

	client.EXPECT().CompleteJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCompleteJobCommand(client, utils.DefaultTestTimeout)

	variablesCommand, err := command.JobKey(123).VariablesFromStringer(DataType{Foo: "bar"})
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

func TestCompleteJobCommandWithVariablesFromObject(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.CompleteJobRequest{
		JobKey:    123,
		Variables: variables,
	}
	stub := &pb.CompleteJobResponse{}

	client.EXPECT().CompleteJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCompleteJobCommand(client, utils.DefaultTestTimeout)

	variablesCommand, err := command.JobKey(123).VariablesFromObject(DataType{Foo: "bar"})
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

func TestCompleteJobCommandWithVariablesFromObjectOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{}"

	request := &pb.CompleteJobRequest{
		JobKey:    123,
		Variables: variables,
	}
	stub := &pb.CompleteJobResponse{}

	client.EXPECT().CompleteJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCompleteJobCommand(client, utils.DefaultTestTimeout)

	variablesCommand, err := command.JobKey(123).VariablesFromObject(DataType{Foo: ""})
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

func TestCompleteJobCommandWithVariablesFromObjectIgnoreOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"\"}"

	request := &pb.CompleteJobRequest{
		JobKey:    123,
		Variables: variables,
	}
	stub := &pb.CompleteJobResponse{}

	client.EXPECT().CompleteJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCompleteJobCommand(client, utils.DefaultTestTimeout)

	variablesCommand, err := command.JobKey(123).VariablesFromObjectIgnoreOmitempty(DataType{Foo: ""})
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

func TestCompleteJobCommandWithVariablesFromMap(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"
	variableMaps := make(map[string]interface{})
	variableMaps["foo"] = "bar"

	request := &pb.CompleteJobRequest{
		JobKey:    123,
		Variables: variables,
	}
	stub := &pb.CompleteJobResponse{}

	client.EXPECT().CompleteJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCompleteJobCommand(client, utils.DefaultTestTimeout)

	variablesCommand, err := command.JobKey(123).VariablesFromMap(variableMaps)
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
