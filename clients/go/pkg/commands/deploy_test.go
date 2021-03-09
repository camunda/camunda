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
	"github.com/golang/mock/gomock"
	"github.com/camunda-cloud/zeebe/clients/go/internal/mock_pb"
	"github.com/camunda-cloud/zeebe/clients/go/internal/utils"
	"github.com/camunda-cloud/zeebe/clients/go/pkg/pb"
	"io/ioutil"
	"testing"
)

func TestDeployCommand_AddResourceFile(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	demoName := "../../../java/src/test/resources/workflows/demo-process.bpmn"
	demoBytes := readBytes(t, demoName)
	anotherName := "../../../java/src/test/resources/workflows/another-demo-process.bpmn"
	anotherBytes := readBytes(t, anotherName)
	yamlName := "../../../java/src/test/resources/workflows/simple-workflow.yaml"
	yamlBytes := readBytes(t, yamlName)

	request := &pb.DeployWorkflowRequest{
		Workflows: []*pb.WorkflowRequestObject{
			{
				Name:       demoName,
				Type:       pb.WorkflowRequestObject_FILE,
				Definition: demoBytes,
			},
			{
				Name:       anotherName,
				Type:       pb.WorkflowRequestObject_FILE,
				Definition: anotherBytes,
			},
			{
				Name:       yamlName,
				Type:       pb.WorkflowRequestObject_FILE,
				Definition: yamlBytes,
			},
		},
	}
	stub := &pb.DeployWorkflowResponse{}

	client.EXPECT().DeployWorkflow(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewDeployCommand(client, func(context.Context, error) bool { return false })

	response, err := command.
		AddResourceFile(demoName).
		AddResourceFile(anotherName).
		AddResourceFile(yamlName).
		Send(context.Background())

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestDeployCommand_AddResource(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	demoName := "../../../java/src/test/resources/workflows/demo-process.bpmn"
	demoBytes := readBytes(t, demoName)

	request := &pb.DeployWorkflowRequest{
		Workflows: []*pb.WorkflowRequestObject{
			{
				Name:       demoName,
				Type:       pb.WorkflowRequestObject_BPMN,
				Definition: demoBytes,
			},
		},
	}
	stub := &pb.DeployWorkflowResponse{}

	client.EXPECT().DeployWorkflow(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewDeployCommand(client, func(context.Context, error) bool { return false })

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := command.
		AddResource(demoBytes, demoName, pb.WorkflowRequestObject_BPMN).
		Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func readBytes(t *testing.T, filename string) []byte {
	bytes, err := ioutil.ReadFile(filename)
	if err != nil {
		t.Error("Failed to read file ", err)
	}

	return bytes
}
