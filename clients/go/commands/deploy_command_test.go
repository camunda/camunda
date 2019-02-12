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
	"github.com/golang/mock/gomock"
	"github.com/zeebe-io/zeebe/clients/go/mock_pb"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"io/ioutil"
	"testing"
)

func TestDeployCommand_AddResourceFile(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	demoName := "../../java/src/test/resources/workflows/demo-process.bpmn"
	demoBytes := readBytes(t, demoName)
	anotherName := "../../java/src/test/resources/workflows/another-demo-process.bpmn"
	anotherBytes := readBytes(t, anotherName)
	yamlName := "../../java/src/test/resources/workflows/simple-workflow.yaml"
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

	client.EXPECT().DeployWorkflow(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewDeployCommand(client, utils.DefaultTestTimeout)

	response, err := command.
		AddResourceFile(demoName).
		AddResourceFile(anotherName).
		AddResourceFile(yamlName).
		Send()

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

	demoName := "../../java/src/test/resources/workflows/demo-process.bpmn"
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

	client.EXPECT().DeployWorkflow(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewDeployCommand(client, utils.DefaultTestTimeout)

	response, err := command.
		AddResource(demoBytes, demoName, pb.WorkflowRequestObject_BPMN).
		Send()

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
