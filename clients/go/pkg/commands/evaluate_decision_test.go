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

func TestEvaluateDecisionCommandByKey(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.EvaluateDecisionRequest{
		DecisionKey: 123,
	}
	stub := &pb.EvaluateDecisionResponse{
		DecisionKey:     123,
		DecisionId:      "foo",
		DecisionVersion: 4545,
	}

	client.EXPECT().EvaluateDecision(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewEvaluateDecisionCommand(client, func(context.Context, error) bool { return false })

	response, err := command.DecisionKey(123).Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestEvaluateDecisionCommandByDecisionId(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.EvaluateDecisionRequest{
		DecisionId: "foo",
	}
	stub := &pb.EvaluateDecisionResponse{
		DecisionKey:     123,
		DecisionId:      "foo",
		DecisionVersion: 4545,
	}

	client.EXPECT().EvaluateDecision(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewEvaluateDecisionCommand(client, func(context.Context, error) bool { return false })

	response, err := command.DecisionId("foo").Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestEvaluateDecisionCommandWithVariablesFromString(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.EvaluateDecisionRequest{
		DecisionKey: 123,
		Variables:   variables,
	}
	stub := &pb.EvaluateDecisionResponse{
		DecisionKey:     123,
		DecisionId:      "foo",
		DecisionVersion: 4545,
	}

	client.EXPECT().EvaluateDecision(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewEvaluateDecisionCommand(client, func(context.Context, error) bool { return false })

	evalDecisionCommand, err := command.DecisionKey(123).VariablesFromString(variables)
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	response, err := evalDecisionCommand.Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestEvaluateDecisionCommandWithVariablesFromStringer(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.EvaluateDecisionRequest{
		DecisionKey: 123,
		Variables:   variables,
	}
	stub := &pb.EvaluateDecisionResponse{
		DecisionKey:     123,
		DecisionId:      "foo",
		DecisionVersion: 4545,
	}

	client.EXPECT().EvaluateDecision(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewEvaluateDecisionCommand(client, func(context.Context, error) bool { return false })

	evalDecisionCommand, err := command.DecisionKey(123).VariablesFromStringer(DataType{Foo: "bar"})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	response, err := evalDecisionCommand.Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestEvaluateDecisionCommandWithVariablesFromObject(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.EvaluateDecisionRequest{
		DecisionKey: 123,
		Variables:   variables,
	}
	stub := &pb.EvaluateDecisionResponse{
		DecisionKey:     123,
		DecisionId:      "foo",
		DecisionVersion: 4545,
	}

	client.EXPECT().EvaluateDecision(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewEvaluateDecisionCommand(client, func(context.Context, error) bool { return false })

	evalDecisionCommand, err := command.DecisionKey(123).VariablesFromObject(DataType{Foo: "bar"})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	response, err := evalDecisionCommand.Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestEvaluateDecisionCommandWithVariablesFromObjectOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{}"

	request := &pb.EvaluateDecisionRequest{
		DecisionKey: 123,
		Variables:   variables,
	}
	stub := &pb.EvaluateDecisionResponse{
		DecisionKey:     123,
		DecisionId:      "foo",
		DecisionVersion: 4545,
	}

	client.EXPECT().EvaluateDecision(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewEvaluateDecisionCommand(client, func(context.Context, error) bool { return false })

	evalDecisionCommand, err := command.DecisionKey(123).VariablesFromObject(DataType{Foo: ""})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	response, err := evalDecisionCommand.Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestEvaluateDecisionCommandWithVariablesFromObjectIgnoreOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"\"}"

	request := &pb.EvaluateDecisionRequest{
		DecisionKey: 123,
		Variables:   variables,
	}
	stub := &pb.EvaluateDecisionResponse{
		DecisionKey:     123,
		DecisionId:      "foo",
		DecisionVersion: 4545,
	}

	client.EXPECT().EvaluateDecision(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewEvaluateDecisionCommand(client, func(context.Context, error) bool { return false })

	evalDecisionCommand, err := command.DecisionKey(123).VariablesFromObjectIgnoreOmitempty(DataType{Foo: ""})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	response, err := evalDecisionCommand.Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestEvaluateDecisionCommandWithVariablesFromMap(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"
	variablesMap := make(map[string]interface{})
	variablesMap["foo"] = "bar"

	request := &pb.EvaluateDecisionRequest{
		DecisionKey: 123,
		Variables:   variables,
	}
	stub := &pb.EvaluateDecisionResponse{
		DecisionKey:     123,
		DecisionId:      "foo",
		DecisionVersion: 4545,
	}

	client.EXPECT().EvaluateDecision(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewEvaluateDecisionCommand(client, func(context.Context, error) bool { return false })

	evalDecisionCommand, err := command.DecisionKey(123).VariablesFromMap(variablesMap)
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	response, err := evalDecisionCommand.Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}
