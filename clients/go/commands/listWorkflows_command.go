package commands

import (
	"context"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"time"
)

type ListWorkflowsStep1 interface {
	DispatchListWorkflowsCommand
	BpmnProcessId(string) ListWorkflowsStep1
}

type DispatchListWorkflowsCommand interface {
	Send() (*pb.ListWorkflowsResponse, error)
}

type ListWorkflowsCommand struct {
	gateway pb.GatewayClient
	request *pb.ListWorkflowsRequest
}

func (cmd *ListWorkflowsCommand) BpmnProcessId(bpmnProcessId string) ListWorkflowsStep1 {
	cmd.request.BpmnProcessId = bpmnProcessId
	return cmd
}

func (cmd *ListWorkflowsCommand) Send() (*pb.ListWorkflowsResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), utils.StreamTimeoutInSec*time.Second)
	defer cancel()

	return cmd.gateway.ListWorkflows(ctx, cmd.request)
}

func NewListWorkflowsCommand(gateway pb.GatewayClient) ListWorkflowsStep1 {
	return &ListWorkflowsCommand{
		gateway: gateway,
		request: &pb.ListWorkflowsRequest{},
	}
}
