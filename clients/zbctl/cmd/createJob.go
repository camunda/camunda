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

	"github.com/zeebe-io/zeebe/clients/zbctl/utils"

	"os"

	"github.com/spf13/cobra"
	"github.com/zeebe-io/zeebe/clients/go"
)

var (
	retryCountFlag int32
	jobPayloadFlag string
	headersFlag    string
)

// createJobCmd implements cobra command for CLI
var createJobCmd = &cobra.Command{
	Use:   "job <type>",
	Short: "Creates a new job with specified type",
	Long:  ``,
	PreRun: func(cmd *cobra.Command, args []string) {
		initBroker(cmd)
	},
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) == 0 {
			fmt.Println("You must specify job type name as positional argument.")
			os.Exit(utils.ExitCodeIOError)
		}

		client, err := zbc.NewZBClient(brokerAddr)
		utils.CheckOrExit(err, utils.ExitCodeConfigurationError, defaultErrCtx)

		zbCmd, err := client.
			NewCreateJobCommand().
			JobType(args[0]).
			Retries(retryCountFlag).
			SetCustomHeadersFromString(headersFlag)
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)

		zbCmd, err = zbCmd.PayloadFromString(jobPayloadFlag)
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)

		response, err := zbCmd.Send()
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)

		out.Serialize(response).Flush()
	},
}

func init() {
	createJobCmd.
		Flags().
		StringVar(&jobPayloadFlag, "payload", "{}", "Specify payload as JSON string")

	createJobCmd.
		Flags().
		StringVar(&headersFlag, "headers", "{}", "Specify custom headers as JSON string")

	createCmd.AddCommand(createJobCmd)
}
