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
	"github.com/spf13/cobra"
	"github.com/zeebe-io/zeebe/clients/zbctl/utils"
	"log"
)

var updatePayloadFlag string

// updatePayloadCmd represents the updatePayload command
var updatePayloadCmd = &cobra.Command{
	Use:   "payload <activityInstanceKey>",
	Short: "Update the payload of a workflow instance",
	Args: cobra.ExactArgs(1),
	PreRun: initBroker,
	Run: func(cmd *cobra.Command, args []string) {
		activityInstanceKey := convertToKey(args[0], "Expect activity instance id as only positional argument, got")

		request, err := client.NewUpdatePayloadCommand().ActivityInstanceKey(activityInstanceKey).PayloadFromString(updatePayloadFlag)
		utils.CheckOrExit(err, utils.ExitCodeConfigurationError, defaultErrCtx)

		_, err = request.Send()
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)

		log.Println("Update the payload of activity instance with key", activityInstanceKey, "to", updatePayloadFlag)
	},
}

func init() {
	updateCmd.AddCommand(updatePayloadCmd)

	updatePayloadCmd.Flags().StringVar(&updatePayloadFlag, "payload", utils.EmptyJsonObject, "Specify payload as JSON object string")
	updatePayloadCmd.MarkFlagRequired("payload")
}
