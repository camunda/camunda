package commands

import (
    "github.com/golang/mock/gomock"
    "github.com/zeebe-io/zeebe/clients/go/mock_pb"
    "github.com/zeebe-io/zeebe/clients/go/pb"
    "testing"
)

func TestFailJobCommand(t *testing.T) {
    ctrl := gomock.NewController(t)
    defer ctrl.Finish()

    client := mock_pb.NewMockGatewayClient(ctrl)

    request := &pb.FailJobRequest{
        JobKey: 123,
    }
    stub := &pb.FailJobResponse{}

    client.EXPECT().FailJob(gomock.Any(), &rpcMsg{msg: request}).Return(stub, nil)

    command := NewFailJobCommand(client)

    response, err := command.JobKey(123).Send()

    if err != nil {
        t.Errorf("Failed to send request")
    }

    if response != stub {
        t.Errorf("Failed to receive response")
    }
}
