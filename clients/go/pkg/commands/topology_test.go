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
	"github.com/camunda/zeebe/clients/go/v8/internal/mock_pb"
	"github.com/camunda/zeebe/clients/go/v8/internal/utils"
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
	"github.com/golang/mock/gomock"
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
		GatewayVersion:    "0.23.0-SNAPSHOT",
		Brokers: []*pb.BrokerInfo{
			{
				NodeId:  0,
				Host:    "foo",
				Port:    12,
				Version: "0.23.0-SNAPSHOT",
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
				NodeId:  1,
				Host:    "bar",
				Port:    9237,
				Version: "0.23.0-SNAPSHOT",
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

	client.EXPECT().Topology(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewTopologyCommand(client, func(context.Context, error) bool { return false })

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := command.Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}
