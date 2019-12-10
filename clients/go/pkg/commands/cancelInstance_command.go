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

package commands

import (
	"context"
	"github.com/zeebe-io/zeebe/clients/go/pkg/pb"
	"time"
)

type CancelInstanceStep1 interface {
	WorkflowInstanceKey(int64) DispatchCancelWorkflowInstanceCommand
}

type DispatchCancelWorkflowInstanceCommand interface {
	Send() (*pb.CancelWorkflowInstanceResponse, error)
}

type CancelWorkflowInstanceCommand struct {
	Command
	request pb.CancelWorkflowInstanceRequest
}

func (cmd CancelWorkflowInstanceCommand) Send() (*pb.CancelWorkflowInstanceResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	response, err := cmd.gateway.CancelWorkflowInstance(ctx, &cmd.request)
	if cmd.retryPredicate(err) {
		return cmd.Send()
	}

	return response, err
}

func (cmd CancelWorkflowInstanceCommand) WorkflowInstanceKey(key int64) DispatchCancelWorkflowInstanceCommand {
	cmd.request = pb.CancelWorkflowInstanceRequest{WorkflowInstanceKey: key}
	return cmd
}

func NewCancelInstanceCommand(gateway pb.GatewayClient, requestTimeout time.Duration, retryPredicate func(error) bool) CancelInstanceStep1 {
	return &CancelWorkflowInstanceCommand{
		Command: Command{
			gateway:        gateway,
			requestTimeout: requestTimeout,
			retryPredicate: retryPredicate,
		},
	}
}
