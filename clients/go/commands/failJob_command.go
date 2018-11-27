package commands

import (
	"context"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"time"
)

type DispatchFailJobCommand interface {
	Send() (*pb.FailJobResponse, error)
}

type FailJobCommandStep1 interface {
	JobKey(int64) FailJobCommandStep2
}

type FailJobCommandStep2 interface {
	Retries(int32) FailJobCommandStep3
}

type FailJobCommandStep3 interface {
	DispatchFailJobCommand
	ErrorMessage(string) FailJobCommandStep3
}

type FailJobCommand struct {
	request        *pb.FailJobRequest
	gateway        pb.GatewayClient
	requestTimeout time.Duration
}

func (cmd *FailJobCommand) JobKey(jobKey int64) FailJobCommandStep2 {
	cmd.request.JobKey = jobKey
	return cmd
}

func (cmd *FailJobCommand) Retries(retries int32) FailJobCommandStep3 {
	cmd.request.Retries = retries
	return cmd
}

func (cmd *FailJobCommand) ErrorMessage(errorMessage string) FailJobCommandStep3 {
	cmd.request.ErrorMessage = errorMessage
	return cmd
}

func (cmd *FailJobCommand) Send() (*pb.FailJobResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	return cmd.gateway.FailJob(ctx, cmd.request)
}

func NewFailJobCommand(gateway pb.GatewayClient, requestTimeout time.Duration) FailJobCommandStep1 {
	return &FailJobCommand{
		request:        &pb.FailJobRequest{},
		gateway:        gateway,
		requestTimeout: requestTimeout,
	}
}
