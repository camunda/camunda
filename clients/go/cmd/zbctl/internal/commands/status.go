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
	"fmt"
	"github.com/spf13/cobra"
	"github.com/zeebe-io/zeebe/clients/go/pkg/pb"
	"sort"
)

type ByNodeId []*pb.BrokerInfo

func (a ByNodeId) Len() int           { return len(a) }
func (a ByNodeId) Swap(i, j int)      { a[i], a[j] = a[j], a[i] }
func (a ByNodeId) Less(i, j int) bool { return a[i].NodeId < a[j].NodeId }

type ByPartitionId []*pb.Partition

func (a ByPartitionId) Len() int           { return len(a) }
func (a ByPartitionId) Swap(i, j int)      { a[i], a[j] = a[j], a[i] }
func (a ByPartitionId) Less(i, j int) bool { return a[i].PartitionId < a[j].PartitionId }

var statusCmd = &cobra.Command{
	Use:     "status",
	Short:   "Checks the current status of the cluster",
	Args:    cobra.NoArgs,
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		var err error

		ctx, cancel := context.WithTimeout(context.Background(), defaultTimeout)
		defer cancel()

		topResp, err := client.NewTopologyCommand().Send(ctx)
		if err != nil {
			return err
		}

		ctx, cancel = context.WithTimeout(context.Background(), defaultTimeout)
		defer cancel()

		gatewayVersion := "unavailable"
		verResp, err := client.NewGatewayVersionCommand().Send(ctx)
		if err != nil {
			return err
		} else if verResp.Version != "" {
			gatewayVersion = verResp.Version
		}

		fmt.Println("Cluster size:", topResp.ClusterSize)
		fmt.Println("Partitions count:", topResp.PartitionsCount)
		fmt.Println("Replication factor:", topResp.ReplicationFactor)
		fmt.Println("Gateway version:", gatewayVersion)
		fmt.Println("Brokers:")

		sort.Sort(ByNodeId(topResp.Brokers))

		for _, broker := range topResp.Brokers {
			fmt.Println("  Broker", broker.NodeId, "-", fmt.Sprintf("%s:%d", broker.Host, broker.Port))
			sort.Sort(ByPartitionId(broker.Partitions))
			for _, partition := range broker.Partitions {
				fmt.Println("    Partition", partition.PartitionId, ":", roleToString(partition.Role))
			}
		}

		return nil
	},
}

func init() {
	rootCmd.AddCommand(statusCmd)
}

func roleToString(role pb.Partition_PartitionBrokerRole) string {
	switch role {
	case pb.Partition_LEADER:
		return "Leader"
	case pb.Partition_FOLLOWER:
		return "Follower"
	default:
		return "Unknown"
	}
}
