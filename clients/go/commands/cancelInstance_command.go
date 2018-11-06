package commands

import (
	"context"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"time"
)

type CancelInstanceStep1 interface {
	WorkflowInstanceKey(int64) DispatchCancelWorkflowInstanceCommand
}

type DispatchCancelWorkflowInstanceCommand interface {
	Send() (*pb.CancelWorkflowInstanceResponse, error)
}

type CancelWorkflowInstanceCommand struct {
	request        *pb.CancelWorkflowInstanceRequest
	gateway        pb.GatewayClient
	requestTimeout time.Duration
}

func (cmd CancelWorkflowInstanceCommand) Send() (*pb.CancelWorkflowInstanceResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	return cmd.gateway.CancelWorkflowInstance(ctx, cmd.request)
}

func (cmd CancelWorkflowInstanceCommand) WorkflowInstanceKey(key int64) DispatchCancelWorkflowInstanceCommand {
	cmd.request = &pb.CancelWorkflowInstanceRequest{WorkflowInstanceKey: key}
	return cmd
}

func NewCancelInstanceCommand(gateway pb.GatewayClient, requestTimeout time.Duration) CancelInstanceStep1 {
	return &CancelWorkflowInstanceCommand{
		gateway:        gateway,
		requestTimeout: requestTimeout,
	}
}
