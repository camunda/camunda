package commands

import (
    "context"
    "encoding/json"
    "fmt"
    "github.com/zeebe-io/zeebe/clients/go/pb"
    "time"
)

var ErrNotValidJsonString = fmt.Errorf("not valid json string")

type PublishMessageCommandStep1 interface {
    MessageName(string) PublishMessageCommandStep2
}

type PublishMessageCommandStep2 interface {
    CorrelationKey(string) PublishMessageCommandStep3
}

type PublishMessageCommandStep3 interface {
    DispatchPublishMessageCommand

    MessageId(string) PublishMessageCommandStep3
    TimeToLive(duration time.Duration) PublishMessageCommandStep3

    // Expected to be valid JSON string
    PayloadFromString(string) (PublishMessageCommandStep3, error)

    // Expected to consutrct a valid JSON string
    PayloadFromStringer(fmt.Stringer) (PublishMessageCommandStep3, error)

    // Expected that object is JSON serializable
    PayloadFromObject(interface{}) (PublishMessageCommandStep3, error)
    PayloadFromMap(map[string]interface{}) (PublishMessageCommandStep3, error)
}

type DispatchPublishMessageCommand interface {
    Send() error
}

type PublishMessageCommand struct {
    request *pb.PublishMessageRequest
    gateway pb.GatewayClient
}

func (cmd *PublishMessageCommand) MessageId(messageId string) PublishMessageCommandStep3 {
    cmd.request.MessageId = messageId
    return cmd
}

func (cmd *PublishMessageCommand) serializePayload(payload interface{}) (*string, error) {
    b, err := json.Marshal(payload)
    if err != nil {
        return nil, err
    }
    jsonStringPayload := string(b)
    return &jsonStringPayload, nil
}

func (cmd *PublishMessageCommand) validateJsonString(jsonString string) bool {
    payload := make(map[string]interface{})
    return json.Unmarshal([]byte(jsonString), &payload) == nil
}

func (cmd *PublishMessageCommand) PayloadFromObject(payload interface{}) (PublishMessageCommandStep3, error) {
    jsonStringPayload, err := cmd.serializePayload(payload)
    if err != nil || jsonStringPayload == nil {
        return nil, err
    }
    cmd.request.Payload = *jsonStringPayload
    return cmd, nil
}

func (cmd *PublishMessageCommand) PayloadFromMap(payload map[string]interface{}) (PublishMessageCommandStep3, error) {
    return cmd.PayloadFromObject(payload)
}

func (cmd *PublishMessageCommand) PayloadFromString(payload string) (PublishMessageCommandStep3, error) {
    if cmd.validateJsonString(payload) {
        cmd.request.Payload = payload
        return cmd, nil
    }
    return nil, ErrNotValidJsonString
}

func (cmd *PublishMessageCommand) PayloadFromStringer(payload fmt.Stringer) (PublishMessageCommandStep3, error) {
    return cmd.PayloadFromString(payload.String())
}

func (cmd *PublishMessageCommand) TimeToLive(duration time.Duration) PublishMessageCommandStep3 {
    cmd.request.TimeToLive = int64(duration / time.Millisecond)
    return cmd
}

func (cmd *PublishMessageCommand) CorrelationKey(key string) PublishMessageCommandStep3 {
    cmd.request.CorrelationKey = key
    return cmd
}

func (cmd *PublishMessageCommand) MessageName(name string) PublishMessageCommandStep2 {
    cmd.request.Name = name
    return cmd
}

func (cmd *PublishMessageCommand) Send() error {
    ctx, cancel := context.WithTimeout(context.Background(), RequestTimeoutInSec * time.Second)
    defer cancel()

    _, err := cmd.gateway.PublishMessage(ctx, cmd.request)
    return err
}


func NewPublishMessageCommand(gateway pb.GatewayClient) PublishMessageCommandStep1 {
    return &PublishMessageCommand{
        request: &pb.PublishMessageRequest{},
        gateway: gateway,
    }
}

