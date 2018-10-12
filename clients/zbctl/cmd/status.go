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
package cmd

import (
	"fmt"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/zbctl/utils"

	"github.com/spf13/cobra"
)

// deployWorkflowCmd implements cobra command for cli
var statusCmd = &cobra.Command{
	Use:   "status",
	Short: "Checks the current status of the cluster",
	Args: cobra.NoArgs,
	PreRun: initBroker,
	Run: func(cmd *cobra.Command, args []string) {
		response, err := client.NewTopologyCommand().Send()
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)

		for _, broker := range response.Brokers {
			fmt.Println("Broker", broker.Host, ":", broker.Port)
			for _, partition := range broker.Partitions {
				fmt.Println("  Partition", partition.PartitionId, ":", roleToString(partition.Role))
			}
		}
	},
}

func init() {
	rootCmd.AddCommand(statusCmd)
}

func roleToString(role pb.Partition_PartitionBrokerRole) string {
	switch role {
	case  pb.Partition_LEADER:
		return "Leader"
	case pb.Partition_FOLLOW:
		return "Follower"
	default:
		return "Unknown"
	}
}
