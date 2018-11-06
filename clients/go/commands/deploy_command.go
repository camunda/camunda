package commands

import (
	"context"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"io/ioutil"
	"log"
	"time"
)

type DeployCommand struct {
	gateway        pb.GatewayClient
	requestTimeout time.Duration
	request        *pb.DeployWorkflowRequest
}

func (cmd *DeployCommand) AddResourceFile(path string) *DeployCommand {
	b, err := ioutil.ReadFile(path)
	if err != nil {
		log.Fatal(err)
	}

	cmd.request.Workflows = append(cmd.request.Workflows, &pb.WorkflowRequestObject{Definition: b, Name: path, Type: pb.WorkflowRequestObject_FILE})
	return cmd
}

func (cmd *DeployCommand) Send() (*pb.DeployWorkflowResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	return cmd.gateway.DeployWorkflow(ctx, cmd.request)
}

func NewDeployCommand(gateway pb.GatewayClient, requestTimeout time.Duration) *DeployCommand {
	return &DeployCommand{
		gateway:        gateway,
		requestTimeout: requestTimeout,
		request:        &pb.DeployWorkflowRequest{},
	}
}
