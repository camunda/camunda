package commands

import (
	"context"
	"time"

	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
)

type DispatchResolveIncidentCommand interface {
	Send() (*pb.ResolveIncidentResponse, error)
}

type ResolveIncidentCommandStep1 interface {
	IncidentKey(int64) ResolveIncidentCommandStep2
}

type ResolveIncidentCommandStep2 interface {
	DispatchResolveIncidentCommand
}

type ResolveIncidentCommand struct {
	utils.SerializerMixin

	request        *pb.ResolveIncidentRequest
	gateway        pb.GatewayClient
	requestTimeout time.Duration
}

func (cmd *ResolveIncidentCommand) IncidentKey(incidentKey int64) ResolveIncidentCommandStep2 {
	cmd.request.IncidentKey = incidentKey
	return cmd
}

func (cmd *ResolveIncidentCommand) Send() (*pb.ResolveIncidentResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	return cmd.gateway.ResolveIncident(ctx, cmd.request)
}

func NewResolveIncidentCommand(gateway pb.GatewayClient, requestTimeout time.Duration) ResolveIncidentCommandStep1 {
	return &ResolveIncidentCommand{
		SerializerMixin: utils.NewJsonStringSerializer(),
		request:         &pb.ResolveIncidentRequest{},
		gateway:         gateway,
		requestTimeout:  requestTimeout,
	}
}
