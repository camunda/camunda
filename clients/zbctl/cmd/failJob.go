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
	"github.com/zeebe-io/zeebe/clients/zbctl/utils"
	"log"

	"github.com/spf13/cobra"
)

var failJobRetriesFlag int32

// failJobCmd represents the failJob command
var failJobCmd = &cobra.Command{
	Use:   "job <jobKey>",
	Short: "Fail a job",
	Args: cobra.ExactArgs(1),
	PreRun: initBroker,
	Run: func(cmd *cobra.Command, args []string) {
		jobKey := convertToKey(args[0], "Expect job key as only positional argument, got")

		_, err := client.NewFailJobCommand().JobKey(jobKey).Retries(failJobRetriesFlag).Send()
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)

		log.Println("Failed job with key", jobKey, "and set remaining retries to", failJobRetriesFlag)
	},
}

func init() {
	failCmd.AddCommand(failJobCmd)
	failJobCmd.Flags().Int32Var(&failJobRetriesFlag, "retries", utils.DefaultJobRetries, "Specify remaining retries of job")
	failJobCmd.MarkFlagRequired("retries")
}
