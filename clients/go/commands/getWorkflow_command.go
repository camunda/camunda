package commands

import (
	"context"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"time"
)

type GetWorkflowStep1 interface {
	BpmnProcessId(string) GetWorkflowStep2
	WorkflowKey(int64) GetWorkflowStep3
}

type GetWorkflowStep2 interface {
	Version(int32) GetWorkflowStep3
	LatestVersion() GetWorkflowStep3
}

type GetWorkflowStep3 interface {
	DispatchGetWorkflowCommand
}

type DispatchGetWorkflowCommand interface {
	Send() (*pb.GetWorkflowResponse, error)
}

type GetWorkflowCommand struct {
	gateway        pb.GatewayClient
	request        *pb.GetWorkflowRequest
	requestTimeout time.Duration
}

func (cmd *GetWorkflowCommand) BpmnProcessId(bpmnProcessId string) GetWorkflowStep2 {
	cmd.request.BpmnProcessId = bpmnProcessId
	return cmd
}

func (cmd *GetWorkflowCommand) WorkflowKey(workflowKey int64) GetWorkflowStep3 {
	cmd.request.WorkflowKey = workflowKey
	return cmd
}

func (cmd *GetWorkflowCommand) Version(version int32) GetWorkflowStep3 {
	cmd.request.Version = version
	return cmd
}

func (cmd *GetWorkflowCommand) LatestVersion() GetWorkflowStep3 {
	return cmd.Version(LatestVersion)
}

func (cmd *GetWorkflowCommand) Send() (*pb.GetWorkflowResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	return cmd.gateway.GetWorkflow(ctx, cmd.request)
}

func NewGetWorkflowCommand(gateway pb.GatewayClient, requestTimeout time.Duration) GetWorkflowStep1 {
	return &GetWorkflowCommand{
		gateway:        gateway,
		request:        &pb.GetWorkflowRequest{},
		requestTimeout: requestTimeout,
	}
}
