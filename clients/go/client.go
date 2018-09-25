package zbc

import (
	"github.com/zeebe-io/zeebe/clients/go/commands"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"google.golang.org/grpc"
)

type ZBClientImpl struct {
	gateway    pb.GatewayClient
	connection *grpc.ClientConn
}

func (client *ZBClientImpl) NewHealthCheckCommand() *commands.HealthCheckCommand {
	return commands.NewHealthCheckCommand(client.gateway)
}

func (client *ZBClientImpl) NewDeployWorkflowCommand() *commands.DeployCommand {
	return commands.NewDeployCommand(client.gateway)
}

func (client *ZBClientImpl) NewPublishMessageCommand() commands.PublishMessageCommandStep1 {
	return commands.NewPublishMessageCommand(client.gateway)
}

func (client *ZBClientImpl) NewCreateInstanceCommand() commands.CreateInstanceCommandStep1 {
	return commands.NewCreateInstanceCommand(client.gateway)
}

func (client *ZBClientImpl) Close() error {
	return client.connection.Close()
}

func NewZBClient(gatewayAddress string) (ZBClient, error) {
	var opts []grpc.DialOption
	opts = append(opts, grpc.WithInsecure())

	conn, err := grpc.Dial(gatewayAddress, opts...)
	if err != nil {
		return nil, err
	}
	return &ZBClientImpl{
		gateway:    pb.NewGatewayClient(conn),
		connection: conn,
	}, nil
}
