package commands

import (
    "context"
    "github.com/zeebe-io/zeebe/clients/go/pb"
    "github.com/zeebe-io/zeebe/clients/go/utils"
    "time"
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
	utils.SerializerMixin

	request *pb.UpdateJobRetriesRequest
	gateway pb.GatewayClient
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
	ctx, cancel := context.WithTimeout(context.Background(), utils.RequestTimeoutInSec*time.Second)
	defer cancel()

	return cmd.gateway.UpdateJobRetries(ctx, cmd.request)
}

func NewUpdateJobRetriesCommand(gateway pb.GatewayClient) UpdateJobRetriesCommandStep1 {
	return &UpdateJobRetriesCommand{
		SerializerMixin: utils.NewJsonStringSerializer(),
		request: &pb.UpdateJobRetriesRequest{
			Retries: utils.DefaultRetries,
		},
		gateway: gateway,
	}
}
