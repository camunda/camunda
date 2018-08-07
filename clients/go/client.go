package zbc

import (
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"google.golang.org/grpc"
	"context"
	"time"
)

const RequestTimeoutInSec = 5

type ZBClientImpl struct {
	gateway pb.GatewayClient
	connection *grpc.ClientConn
}

func (client *ZBClientImpl) HealthCheck() (*pb.HealthResponse, error) {
	request := &pb.HealthRequest{}
	ctx, cancel := context.WithTimeout(context.Background(), RequestTimeoutInSec*time.Second)
	defer cancel()

	return client.gateway.Health(ctx, request)
}

func NewZBClient(gatewayAddress string) (*ZBClientImpl, error) {
	var opts []grpc.DialOption
	opts = append(opts, grpc.WithInsecure())

	conn, err := grpc.Dial(gatewayAddress, opts...)
	if err != nil {
		return nil, err
	}
	return &ZBClientImpl{
		gateway: pb.NewGatewayClient(conn),
		connection: conn,
	}, nil
}