package commands

import (
	"context"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"time"
)

type HealthCheckCommand struct {
	gateway pb.GatewayClient
}

func (cmd *HealthCheckCommand) Send() (*pb.HealthResponse, error) {
	request := &pb.HealthRequest{}
	ctx, cancel := context.WithTimeout(context.Background(), utils.RequestTimeoutInSec*time.Second)
	defer cancel()

	return cmd.gateway.Health(ctx, request)
}

func NewHealthCheckCommand(gateway pb.GatewayClient) *HealthCheckCommand {
	return &HealthCheckCommand{
		gateway: gateway,
	}
}
