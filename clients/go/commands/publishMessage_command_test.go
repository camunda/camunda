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
	"github.com/golang/mock/gomock"
	"github.com/zeebe-io/zeebe/clients/go/mock_pb"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"testing"
	"time"
)

func TestPublishMessageCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.PublishMessageRequest{
		Name:           "foo",
		CorrelationKey: "bar",
	}
	stub := &pb.PublishMessageResponse{}

	client.EXPECT().PublishMessage(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewPublishMessageCommand(client, utils.DefaultTestTimeout)

	response, err := command.MessageName("foo").CorrelationKey("bar").Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestPublishMessageCommandWithMessageId(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.PublishMessageRequest{
		Name:           "foo",
		CorrelationKey: "bar",
		MessageId:      "hello",
	}
	stub := &pb.PublishMessageResponse{}

	client.EXPECT().PublishMessage(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewPublishMessageCommand(client, utils.DefaultTestTimeout)

	response, err := command.MessageName("foo").CorrelationKey("bar").MessageId("hello").Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestPublishMessageCommandWithTimeToLive(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.PublishMessageRequest{
		Name:           "foo",
		CorrelationKey: "bar",
		TimeToLive:     6 * 60 * 1000,
	}
	stub := &pb.PublishMessageResponse{}

	client.EXPECT().PublishMessage(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewPublishMessageCommand(client, utils.DefaultTestTimeout)

	response, err := command.MessageName("foo").CorrelationKey("bar").TimeToLive(time.Duration(6 * time.Minute)).Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestPublishMessageCommandWithPayloadFromString(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"

	request := &pb.PublishMessageRequest{
		Name:           "foo",
		CorrelationKey: "bar",
		Payload:        payload,
	}
	stub := &pb.PublishMessageResponse{}

	client.EXPECT().PublishMessage(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewPublishMessageCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.MessageName("foo").CorrelationKey("bar").PayloadFromString(payload)
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

func TestPublishMessageCommandWithPayloadFromStringer(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"

	request := &pb.PublishMessageRequest{
		Name:           "foo",
		CorrelationKey: "bar",
		Payload:        payload,
	}
	stub := &pb.PublishMessageResponse{}

	client.EXPECT().PublishMessage(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewPublishMessageCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.MessageName("foo").CorrelationKey("bar").PayloadFromStringer(DataType{Foo: "bar"})
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

func TestPublishMessageCommandWithPayloadFromObject(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"

	request := &pb.PublishMessageRequest{
		Name:           "foo",
		CorrelationKey: "bar",
		Payload:        payload,
	}
	stub := &pb.PublishMessageResponse{}

	client.EXPECT().PublishMessage(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewPublishMessageCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.MessageName("foo").CorrelationKey("bar").PayloadFromObject(DataType{Foo: "bar"})
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

func TestPublishMessageCommandWithPayloadFromObjectOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{}"

	request := &pb.PublishMessageRequest{
		Name:           "foo",
		CorrelationKey: "bar",
		Payload:        payload,
	}
	stub := &pb.PublishMessageResponse{}

	client.EXPECT().PublishMessage(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewPublishMessageCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.MessageName("foo").CorrelationKey("bar").PayloadFromObject(DataType{Foo: ""})
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

func TestPublishMessageCommandWithPayloadFromObjectIgnoreOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"\"}"

	request := &pb.PublishMessageRequest{
		Name:           "foo",
		CorrelationKey: "bar",
		Payload:        payload,
	}
	stub := &pb.PublishMessageResponse{}

	client.EXPECT().PublishMessage(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewPublishMessageCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.MessageName("foo").CorrelationKey("bar").PayloadFromObjectIgnoreOmitempty(DataType{Foo: ""})
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

func TestPublishMessageCommandWithPayloadFromMap(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"
	payloadMap := make(map[string]interface{})
	payloadMap["foo"] = "bar"

	request := &pb.PublishMessageRequest{
		Name:           "foo",
		CorrelationKey: "bar",
		Payload:        payload,
	}
	stub := &pb.PublishMessageResponse{}

	client.EXPECT().PublishMessage(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewPublishMessageCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.MessageName("foo").CorrelationKey("bar").PayloadFromMap(payloadMap)
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
