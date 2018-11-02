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
	"log"
)

var (
	updatePayloadKey  int64
	updatePayloadFlag string
)

var updatePayloadCmd = &cobra.Command{
	Use:     "payload <key>",
	Short:   "Update the payload of a workflow instance",
	Args:    keyArg(&updatePayloadKey),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		request, err := client.NewUpdatePayloadCommand().ElementInstanceKey(updatePayloadKey).PayloadFromString(updatePayloadFlag)
		if err != nil {
			return err
		}

		_, err = request.Send()
		if err == nil {
			log.Println("Updated the payload of element instance with key", updatePayloadKey, "to", updatePayloadFlag)
		}

		return err
	},
}

func init() {
	updateCmd.AddCommand(updatePayloadCmd)

	updatePayloadCmd.Flags().StringVar(&updatePayloadFlag, "payload", "{}", "Specify payload as JSON object string")
	updatePayloadCmd.MarkFlagRequired("payload")
}
