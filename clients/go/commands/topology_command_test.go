package commands

import (
	"github.com/golang/mock/gomock"
	"github.com/zeebe-io/zeebe/clients/go/mock_pb"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"testing"
)

func TestTopologyCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.TopologyRequest{}
	stub := &pb.TopologyResponse{
		ClusterSize:       12,
		PartitionsCount:   23,
		ReplicationFactor: 3,
		Brokers: []*pb.BrokerInfo{
			{
				NodeId: 0,
				Host:   "foo",
				Port:   12,
				Partitions: []*pb.Partition{
					{
						PartitionId: 0,
						Role:        pb.Partition_LEADER,
					},
					{
						PartitionId: 1,
						Role:        pb.Partition_FOLLOWER,
					},
				},
			},
			{
				NodeId: 1,
				Host:   "bar",
				Port:   9237,
				Partitions: []*pb.Partition{
					{
						PartitionId: 1,
						Role:        pb.Partition_LEADER,
					},
					{
						PartitionId: 0,
						Role:        pb.Partition_FOLLOWER,
					},
				},
			},
		},
	}

	client.EXPECT().Topology(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewTopologyCommand(client, utils.DefaultTestTimeout)

	response, err := command.Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}
