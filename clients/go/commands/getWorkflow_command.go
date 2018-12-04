// Copyright © 2018 Camunda Services GmbH (info@camunda.com)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

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
