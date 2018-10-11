package commands

import (
    "context"
    "github.com/zeebe-io/zeebe/clients/go/pb"
    "github.com/zeebe-io/zeebe/clients/go/utils"
    "time"
)

type DispatchFailJobCommand interface {
	Send() (*pb.FailJobResponse, error)
}

type FailJobCommandStep1 interface {
	JobKey(int64) DispatchFailJobCommand
}

type FailJobCommand struct {
	utils.SerializerMixin

	request *pb.FailJobRequest
	gateway pb.GatewayClient
}

func (cmd *FailJobCommand) JobKey(jobKey int64) DispatchFailJobCommand {
	cmd.request.JobKey = jobKey
	return cmd
}

func (cmd *FailJobCommand) Send() (*pb.FailJobResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), utils.RequestTimeoutInSec*time.Second)
	defer cancel()

	return cmd.gateway.FailJob(ctx, cmd.request)
}

func NewFailJobCommand(gateway pb.GatewayClient) FailJobCommandStep1 {
	return &FailJobCommand{
		SerializerMixin: utils.NewJsonStringSerializer(),
		request: &pb.FailJobRequest{},
		gateway: gateway,
	}
}
