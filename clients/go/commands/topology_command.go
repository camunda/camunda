package commands

import (
	"context"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"time"
)

type TopologyCommand struct {
	gateway pb.GatewayClient
}

func (cmd *TopologyCommand) Send() (*pb.TopologyResponse, error) {
	request := &pb.TopologyRequest{}
	ctx, cancel := context.WithTimeout(context.Background(), utils.RequestTimeoutInSec*time.Second)
	defer cancel()

	return cmd.gateway.Topology(ctx, request)
}

func NewTopologyCommand(gateway pb.GatewayClient) *TopologyCommand {
	return &TopologyCommand{
		gateway: gateway,
	}
}
