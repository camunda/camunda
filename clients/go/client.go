package zbc

import (
	"github.com/zeebe-io/zeebe/clients/go/commands"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/worker"
	"google.golang.org/grpc"
)

type ZBClientImpl struct {
	gateway    pb.GatewayClient
	connection *grpc.ClientConn
}

func (client *ZBClientImpl) NewTopologyCommand() *commands.TopologyCommand {
	return commands.NewTopologyCommand(client.gateway)
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

func (client *ZBClientImpl) NewCancelInstanceCommand() commands.CancelInstanceStep1 {
	return commands.NewCancelInstanceCommand(client.gateway)
}

func (client *ZBClientImpl) NewCreateJobCommand() commands.CreateJobCommandStep1 {
	return commands.NewCreateJobCommand(client.gateway)
}

func (client *ZBClientImpl) NewCompleteJobCommand() commands.CompleteJobCommandStep1 {
	return commands.NewCompleteJobCommand(client.gateway)
}

func (client *ZBClientImpl) NewFailJobCommand() commands.FailJobCommandStep1 {
	return commands.NewFailJobCommand(client.gateway)
}

func (client *ZBClientImpl) NewUpdateJobRetriesCommand() commands.UpdateJobRetriesCommandStep1 {
	return commands.NewUpdateJobRetriesCommand(client.gateway)
}

func (client *ZBClientImpl) NewUpdatePayloadCommand() commands.UpdatePayloadCommandStep1 {
	return commands.NewUpdatePayloadCommand(client.gateway)
}

func (client *ZBClientImpl) NewActivateJobsCommand() commands.ActivateJobsCommandStep1 {
	return commands.NewActivateJobsCommand(client.gateway)
}

func (client *ZBClientImpl) NewListWorkflowsCommand() commands.ListWorkflowsStep1 {
	return commands.NewListWorkflowsCommand(client.gateway)
}

func (client *ZBClientImpl) NewGetWorkflowCommand() commands.GetWorkflowStep1 {
	return commands.NewGetWorkflowCommand(client.gateway)
}

func (client *ZBClientImpl) NewJobWorker() worker.JobWorkerBuilderStep1 {
	return worker.NewJobWorkerBuilder(client.gateway, client)
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
