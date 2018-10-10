package commands

import (
    "github.com/golang/mock/gomock"
    "github.com/zeebe-io/zeebe/clients/go/mock_pb"
    "github.com/zeebe-io/zeebe/clients/go/pb"
    "testing"
    "time"
)

func TestPublishMessageCommand(t *testing.T) {
    ctrl := gomock.NewController(t)
    defer ctrl.Finish()

    client := mock_pb.NewMockGatewayClient(ctrl)

    request := &pb.PublishMessageRequest{
        Name: "foo",
        CorrelationKey: "bar",
    }
    stub := &pb.PublishMessageResponse{}

    client.EXPECT().PublishMessage(gomock.Any(), &rpcMsg{msg: request}).Return(stub, nil)

    command := NewPublishMessageCommand(client)

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
        Name: "foo",
        CorrelationKey: "bar",
        MessageId: "hello",
    }
    stub := &pb.PublishMessageResponse{}

    client.EXPECT().PublishMessage(gomock.Any(), &rpcMsg{msg: request}).Return(stub, nil)

    command := NewPublishMessageCommand(client)

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
        Name: "foo",
        CorrelationKey: "bar",
        TimeToLive: 6 * 60 * 1000,
    }
    stub := &pb.PublishMessageResponse{}

    client.EXPECT().PublishMessage(gomock.Any(), &rpcMsg{msg: request}).Return(stub, nil)

    command := NewPublishMessageCommand(client)

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
        Name: "foo",
        CorrelationKey: "bar",
        Payload: payload,
    }
    stub := &pb.PublishMessageResponse{}

    client.EXPECT().PublishMessage(gomock.Any(), &rpcMsg{msg: request}).Return(stub, nil)

    command := NewPublishMessageCommand(client)

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
        Name: "foo",
        CorrelationKey: "bar",
        Payload: payload,
    }
    stub := &pb.PublishMessageResponse{}

    client.EXPECT().PublishMessage(gomock.Any(), &rpcMsg{msg: request}).Return(stub, nil)

    command := NewPublishMessageCommand(client)

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
        Name: "foo",
        CorrelationKey: "bar",
        Payload: payload,
    }
    stub := &pb.PublishMessageResponse{}

    client.EXPECT().PublishMessage(gomock.Any(), &rpcMsg{msg: request}).Return(stub, nil)

    command := NewPublishMessageCommand(client)

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

func TestPublishMessageCommandWithPayloadFromMap(t *testing.T) {
    ctrl := gomock.NewController(t)
    defer ctrl.Finish()

    client := mock_pb.NewMockGatewayClient(ctrl)

    payload := "{\"foo\":\"bar\"}"
    payloadMap := make(map[string]interface{})
    payloadMap["foo"] = "bar"

    request := &pb.PublishMessageRequest{
        Name: "foo",
        CorrelationKey: "bar",
        Payload: payload,
    }
    stub := &pb.PublishMessageResponse{}

    client.EXPECT().PublishMessage(gomock.Any(), &rpcMsg{msg: request}).Return(stub, nil)

    command := NewPublishMessageCommand(client)

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
