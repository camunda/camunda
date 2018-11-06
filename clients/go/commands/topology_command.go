package commands

import (
	"context"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"time"
)

type TopologyCommand struct {
	gateway        pb.GatewayClient
	requestTimeout time.Duration
}

func (cmd *TopologyCommand) Send() (*pb.TopologyResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	request := &pb.TopologyRequest{}
	return cmd.gateway.Topology(ctx, request)
}

func NewTopologyCommand(gateway pb.GatewayClient, requestTimeout time.Duration) *TopologyCommand {
	return &TopologyCommand{
		gateway:        gateway,
		requestTimeout: requestTimeout,
	}
}
