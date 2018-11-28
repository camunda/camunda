package commands

import (
	"testing"

	"github.com/golang/mock/gomock"
	"github.com/zeebe-io/zeebe/clients/go/mock_pb"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
)

func TestResolveIncidentCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.ResolveIncidentRequest{
		IncidentKey: 123,
	}
	stub := &pb.ResolveIncidentResponse{}

	client.EXPECT().ResolveIncident(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewResolveIncidentCommand(client, utils.DefaultTestTimeout)

	response, err := command.IncidentKey(123).Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}
