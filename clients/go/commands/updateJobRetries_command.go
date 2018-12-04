package commands

import (
	"context"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"time"
)

const (
	DefaultJobRetries = 3
)

type DispatchUpdateJobRetriesCommand interface {
	Send() (*pb.UpdateJobRetriesResponse, error)
}

type UpdateJobRetriesCommandStep1 interface {
	JobKey(int64) UpdateJobRetriesCommandStep2
}

type UpdateJobRetriesCommandStep2 interface {
	DispatchUpdateJobRetriesCommand

	Retries(int32) DispatchUpdateJobRetriesCommand
}

type UpdateJobRetriesCommand struct {
	request        *pb.UpdateJobRetriesRequest
	gateway        pb.GatewayClient
	requestTimeout time.Duration
}

func (cmd *UpdateJobRetriesCommand) GetRequest() *pb.UpdateJobRetriesRequest {
	return cmd.request
}

func (cmd *UpdateJobRetriesCommand) JobKey(jobKey int64) UpdateJobRetriesCommandStep2 {
	cmd.request.JobKey = jobKey
	return cmd
}

func (cmd *UpdateJobRetriesCommand) Retries(retries int32) DispatchUpdateJobRetriesCommand {
	cmd.request.Retries = retries
	return cmd
}

func (cmd *UpdateJobRetriesCommand) Send() (*pb.UpdateJobRetriesResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	return cmd.gateway.UpdateJobRetries(ctx, cmd.request)
}

func NewUpdateJobRetriesCommand(gateway pb.GatewayClient, requestTimeout time.Duration) UpdateJobRetriesCommandStep1 {
	return &UpdateJobRetriesCommand{
		request: &pb.UpdateJobRetriesRequest{
			Retries: DefaultJobRetries,
		},
		gateway:        gateway,
		requestTimeout: requestTimeout,
	}
}
