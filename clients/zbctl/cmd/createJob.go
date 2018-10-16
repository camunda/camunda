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

	"github.com/spf13/cobra"
)

var (
	createJobRetriesFlag       int32
	createJobCustomHeadersFlag string
	createJobPayloadFlag       string
)

// createJobCmd implements cobra command for CLI
var createJobCmd = &cobra.Command{
	Use:    "job <type>",
	Short:  "Creates a new job with specified type",
	Args:   cobra.ExactArgs(1),
	PreRun: initBroker,
	Run: func(cmd *cobra.Command, args []string) {
		zbCmd, err := client.
			NewCreateJobCommand().
			JobType(args[0]).
			Retries(createJobRetriesFlag).
			SetCustomHeadersFromString(createJobCustomHeadersFlag)
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)

		zbCmd, err = zbCmd.PayloadFromString(createJobPayloadFlag)
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)

		response, err := zbCmd.Send()
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)

		out.Serialize(response).Flush()
	},
}

func init() {
	createCmd.AddCommand(createJobCmd)

	createJobCmd.Flags().Int32Var(&createJobRetriesFlag, "retries", utils.DefaultJobRetries, "Retries of job")

	createJobCmd.
		Flags().
		StringVar(&createJobCustomHeadersFlag, "headers", utils.EmptyJsonObject, "Specify custom headers as JSON string")

	createJobCmd.
		Flags().
		StringVar(&createJobPayloadFlag, "payload", utils.EmptyJsonObject, "Specify payload as JSON string")
}
