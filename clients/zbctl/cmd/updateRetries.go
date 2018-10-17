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

var updateRetriesFlag int32

// updateRetriesCmd represents the updateRetries command
var updateRetriesCmd = &cobra.Command{
	Use:    "retries <jobKey>",
	Short:  "Update retries of a job",
	Args:   cobra.ExactArgs(1),
	PreRun: initClient,
	Run: func(cmd *cobra.Command, args []string) {
		jobKey := convertToKey(args[0], "Expect job key as only positional argument, got")

		_, err := client.NewUpdateJobRetriesCommand().JobKey(jobKey).Retries(updateRetriesFlag).Send()
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)

		log.Println("Update the retries of job with key", jobKey, "to", updateRetriesFlag)
	},
}

func init() {
	updateCmd.AddCommand(updateRetriesCmd)
	updateRetriesCmd.Flags().Int32Var(&updateRetriesFlag, "retries", utils.DefaultJobRetries, "Specify retries of job")
	updateRetriesCmd.MarkFlagRequired("retries")
}
