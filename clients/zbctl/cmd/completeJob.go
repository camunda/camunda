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
	completeJobKey           int64
	completeJobVariablesFlag string
)

var completeJobCmd = &cobra.Command{
	Use:     "job <key>",
	Short:   "Complete a job",
	Args:    keyArg(&completeJobKey),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		request, err := client.NewCompleteJobCommand().JobKey(completeJobKey).VariablesFromString(completeJobVariablesFlag)
		if err != nil {
			return err
		}

		_, err = request.Send()
		if err == nil {
			log.Println("Completed job with key", completeJobKey, "and variables", completeJobVariablesFlag)
		}
		return err
	},
}

func init() {
	completeCmd.AddCommand(completeJobCmd)
	completeJobCmd.Flags().StringVar(&completeJobVariablesFlag, "variables", "{}", "Specify variables as JSON object string")
}
