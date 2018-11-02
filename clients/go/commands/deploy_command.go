package commands

import (
	"context"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"io/ioutil"
	"log"
	"strings"
	"time"
)

type DeployCommand struct {
	gateway        pb.GatewayClient
	requestTimeout time.Duration
	request        *pb.DeployWorkflowRequest
}

func (cmd *DeployCommand) AddResourceFile(path string) *DeployCommand {
	workflowType := pb.WorkflowRequestObject_BPMN
	if strings.HasSuffix(path, ".yaml") || strings.HasSuffix(path, ".yml") {
		workflowType = pb.WorkflowRequestObject_YAML
	}

	b, err := ioutil.ReadFile(path)
	if err != nil {
		log.Fatal(err)
	}

	cmd.request.Workflows = append(cmd.request.Workflows, &pb.WorkflowRequestObject{Definition: b, Name: path, Type: workflowType})
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
