package commands

import (
	"context"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"time"
)

type CancelInstanceStep1 interface {
	WorkflowInstanceKey(int64) DispatchCancelWorkflowInstanceCommand
}

type DispatchCancelWorkflowInstanceCommand interface {
	Send() (*pb.CancelWorkflowInstanceResponse, error)
}

type CancelWorkflowInstanceCommand struct {
	request *pb.CancelWorkflowInstanceRequest
	gateway pb.GatewayClient
}

func (cmd CancelWorkflowInstanceCommand) Send() (*pb.CancelWorkflowInstanceResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), utils.RequestTimeoutInSec*time.Second)
	defer cancel()

	return cmd.gateway.CancelWorkflowInstance(ctx, cmd.request)
}

func (cmd CancelWorkflowInstanceCommand) WorkflowInstanceKey(key int64) DispatchCancelWorkflowInstanceCommand {
	cmd.request = &pb.CancelWorkflowInstanceRequest{WorkflowInstanceKey: key}
	return cmd
}

func NewCancelInstanceCommand(gateway pb.GatewayClient) CancelInstanceStep1 {
	return &CancelWorkflowInstanceCommand{
		gateway: gateway,
	}
}
