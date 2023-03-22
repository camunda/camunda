// Copyright © 2018 Camunda Services GmbH (info@camunda.com)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//	http://www.apache.org/licenses/LICENSE-2.0
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
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
	"github.com/spf13/cobra"
	"net"
	"sort"
	"strings"
)

type StatusResponseWrapper struct {
	response *pb.TopologyResponse
}

func (s StatusResponseWrapper) json() (string, error) {
	return toJSON(s.response)
}

func (s StatusResponseWrapper) human() (string, error) {
	resp := s.response
	gatewayVersion := "unavailable"
	if resp.GatewayVersion != "" {
		gatewayVersion = resp.GatewayVersion
	}

	var stringBuilder strings.Builder

	stringBuilder.WriteString(fmt.Sprintf("Cluster size: %d\n", resp.ClusterSize))
	stringBuilder.WriteString(fmt.Sprintf("Partitions count: %d\n", resp.PartitionsCount))
	stringBuilder.WriteString(fmt.Sprintf("Replication factor: %d\n", resp.ReplicationFactor))
	stringBuilder.WriteString(fmt.Sprintf("Gateway version: %s\n", gatewayVersion))
	stringBuilder.WriteString("Brokers:\n")

	sort.Sort(ByNodeID(resp.Brokers))

	for b, broker := range resp.Brokers {
		stringBuilder.WriteString(fmt.Sprintf("  Broker %d - %s:%d\n",
			broker.NodeId,
			formatHost(broker.Host),
			broker.Port))
		version := "unavailable"
		if broker.Version != "" {
			version = broker.Version
		}

		stringBuilder.WriteString(fmt.Sprintf("    Version: %s\n", version))

		sort.Sort(ByPartitionID(broker.Partitions))
		for p, partition := range broker.Partitions {
			stringBuilder.WriteString(fmt.Sprintf("    Partition %d : %s, %s",
				partition.PartitionId,
				roleToString(partition.Role),
				healthToString(partition.Health)))

			if p < len(broker.Partitions)-1 || b < len(resp.Brokers)-1 {
				stringBuilder.WriteRune('\n')
			}
		}
	}
	return stringBuilder.String(), nil
}

type ByNodeID []*pb.BrokerInfo

func (a ByNodeID) Len() int           { return len(a) }
func (a ByNodeID) Swap(i, j int)      { a[i], a[j] = a[j], a[i] }
func (a ByNodeID) Less(i, j int) bool { return a[i].NodeId < a[j].NodeId }

type ByPartitionID []*pb.Partition

func (a ByPartitionID) Len() int           { return len(a) }
func (a ByPartitionID) Swap(i, j int)      { a[i], a[j] = a[j], a[i] }
func (a ByPartitionID) Less(i, j int) bool { return a[i].PartitionId < a[j].PartitionId }

var statusCmd = &cobra.Command{
	Use:     "status",
	Short:   "Checks the current status of the cluster",
	Args:    cobra.NoArgs,
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		ctx, cancel := context.WithTimeout(context.Background(), timeoutFlag)
		defer cancel()

		resp, err := client.NewTopologyCommand().Send(ctx)
		if err != nil {
			return err
		}

		err = printOutput(StatusResponseWrapper{resp})
		if err != nil {
			return err
		}
		return nil
	},
}

func init() {
	addOutputFlag(statusCmd)
	rootCmd.AddCommand(statusCmd)
}

const unknownState = "Unknown"

func roleToString(role pb.Partition_PartitionBrokerRole) string {
	switch role {
	case pb.Partition_LEADER:
		return "Leader"
	case pb.Partition_FOLLOWER:
		return "Follower"
	case pb.Partition_INACTIVE:
		return "Inactive"
	default:
		return unknownState
	}
}

func healthToString(health pb.Partition_PartitionBrokerHealth) string {
	switch health {
	case pb.Partition_HEALTHY:
		return "Healthy"
	case pb.Partition_UNHEALTHY:
		return "Unhealthy"
	default:
		return unknownState
	}
}

func formatHost(host string) string {
	ips, err := net.LookupIP(host)
	if err != nil || len(ips) > 0 {
		return host
	}
	ip := net.ParseIP(host)
	if ip.To4() != nil {
		return ip.String()
	}
	return fmt.Sprintf("[%s]", ip.String())
}
