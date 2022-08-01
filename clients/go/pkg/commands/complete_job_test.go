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
	"github.com/camunda/zeebe/clients/go/v8/internal/mock_pb"
	"github.com/camunda/zeebe/clients/go/v8/internal/utils"
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
	"github.com/golang/mock/gomock"
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

	client.EXPECT().CompleteJob(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := NewCompleteJobCommand(client, func(context.Context, error) bool {
		return false
	}).JobKey(123).Send(ctx)

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

	client.EXPECT().CompleteJob(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCompleteJobCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.JobKey(123).VariablesFromString(variables)
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

	client.EXPECT().CompleteJob(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCompleteJobCommand(client, func(context.Context, error) bool {
		return false
	})

	variablesCommand, err := command.JobKey(123).VariablesFromStringer(DataType{Foo: "bar"})
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

	client.EXPECT().CompleteJob(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCompleteJobCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.JobKey(123).VariablesFromObject(DataType{Foo: "bar"})
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

	client.EXPECT().CompleteJob(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCompleteJobCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.JobKey(123).VariablesFromObject(DataType{Foo: ""})
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

	client.EXPECT().CompleteJob(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCompleteJobCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.JobKey(123).VariablesFromObjectIgnoreOmitempty(DataType{Foo: ""})
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

	client.EXPECT().CompleteJob(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewCompleteJobCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.JobKey(123).VariablesFromMap(variableMaps)
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
