package commands

import (
	"context"
	"github.com/zeebe-io/zeebe/clients/go/pb"
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
	gateway        pb.GatewayClient
	request        *pb.ListWorkflowsRequest
	requestTimeout time.Duration
}

func (cmd *ListWorkflowsCommand) BpmnProcessId(bpmnProcessId string) ListWorkflowsStep1 {
	cmd.request.BpmnProcessId = bpmnProcessId
	return cmd
}

func (cmd *ListWorkflowsCommand) Send() (*pb.ListWorkflowsResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	return cmd.gateway.ListWorkflows(ctx, cmd.request)
}

func NewListWorkflowsCommand(gateway pb.GatewayClient, requestTimeout time.Duration) ListWorkflowsStep1 {
	return &ListWorkflowsCommand{
		gateway:        gateway,
		request:        &pb.ListWorkflowsRequest{},
		requestTimeout: requestTimeout,
	}
}
