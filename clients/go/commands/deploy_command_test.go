package commands

import (
	"github.com/golang/mock/gomock"
	"github.com/zeebe-io/zeebe/clients/go/mock_pb"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"io/ioutil"
	"testing"
)

func TestDeployCommand(t *testing.T) {
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
				Type:       pb.WorkflowRequestObject_BPMN,
				Definition: demoBytes,
			},
			{
				Name:       anotherName,
				Type:       pb.WorkflowRequestObject_BPMN,
				Definition: anotherBytes,
			},
			{
				Name:       yamlName,
				Type:       pb.WorkflowRequestObject_YAML,
				Definition: yamlBytes,
			},
		},
	}
	stub := &pb.DeployWorkflowResponse{}

	client.EXPECT().DeployWorkflow(gomock.Any(), &rpcMsg{msg: request}).Return(stub, nil)

	command := NewDeployCommand(client)

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

func readBytes(t *testing.T, filename string) []byte {
	bytes, err := ioutil.ReadFile(filename)
	if err != nil {
		t.Error("Failed to read file ", err)
	}

	return bytes
}
