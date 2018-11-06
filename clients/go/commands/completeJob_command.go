package commands

import (
	"context"
	"fmt"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"time"
)

type DispatchCompleteJobCommand interface {
	Send() (*pb.CompleteJobResponse, error)
}

type CompleteJobCommandStep1 interface {
	JobKey(int64) CompleteJobCommandStep2
}

type CompleteJobCommandStep2 interface {
	DispatchCompleteJobCommand

	PayloadFromString(string) (DispatchCompleteJobCommand, error)
	PayloadFromStringer(fmt.Stringer) (DispatchCompleteJobCommand, error)
	PayloadFromMap(map[string]interface{}) (DispatchCompleteJobCommand, error)
	PayloadFromObject(interface{}) (DispatchCompleteJobCommand, error)
}

type CompleteJobCommand struct {
	utils.SerializerMixin

	request        *pb.CompleteJobRequest
	gateway        pb.GatewayClient
	requestTimeout time.Duration
}

func (cmd *CompleteJobCommand) JobKey(jobKey int64) CompleteJobCommandStep2 {
	cmd.request.JobKey = jobKey
	return cmd
}

func (cmd *CompleteJobCommand) PayloadFromString(payload string) (DispatchCompleteJobCommand, error) {
	err := cmd.Validate("payload", payload)
	if err != nil {
		return nil, err
	}

	cmd.request.Payload = payload
	return cmd, nil
}

func (cmd *CompleteJobCommand) PayloadFromStringer(payload fmt.Stringer) (DispatchCompleteJobCommand, error) {
	return cmd.PayloadFromString(payload.String())
}

func (cmd *CompleteJobCommand) PayloadFromObject(payload interface{}) (DispatchCompleteJobCommand, error) {
	value, err := cmd.AsJson("payload", payload)
	if err != nil {
		return nil, err
	}

	cmd.request.Payload = value
	return cmd, nil
}

func (cmd *CompleteJobCommand) PayloadFromMap(payload map[string]interface{}) (DispatchCompleteJobCommand, error) {
	return cmd.PayloadFromObject(payload)
}

func (cmd *CompleteJobCommand) Send() (*pb.CompleteJobResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	return cmd.gateway.CompleteJob(ctx, cmd.request)
}

func NewCompleteJobCommand(gateway pb.GatewayClient, requestTimeout time.Duration) CompleteJobCommandStep1 {
	return &CompleteJobCommand{
		SerializerMixin: utils.NewJsonStringSerializer(),
		request:         &pb.CompleteJobRequest{},
		gateway:         gateway,
		requestTimeout:  requestTimeout,
	}
}
