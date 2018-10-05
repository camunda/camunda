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
    "strconv"

    "github.com/zeebe-io/zeebe/clients/zbctl/utils"

	"os"

	"github.com/spf13/cobra"
	"github.com/zeebe-io/zeebe/clients/go"
	zbCommands "github.com/zeebe-io/zeebe/clients/go/commands"
)


var cancelInstanceCmd = &cobra.Command{
	Use:   "instance <processId>",
	Short: "Cancels new workflow instance defined by the process ID",
	Long:  ``,
	PreRun: func(cmd *cobra.Command, args []string) {
		initBroker(cmd)
	},
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) == 0 {
			fmt.Println("You must specify workflow instance key as positional arguments.")
			os.Exit(utils.ExitCodeConfigurationError)
		}

        workflowInstanceKey, err := strconv.ParseInt(args[0], 10, 64)
        if err != nil {
            fmt.Println("Invalid format for numerical workflow instance key.")
            utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)
        }

        utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx);

		client, err := zbc.NewZBClient(brokerAddr)
		utils.CheckOrExit(err, utils.ExitCodeConfigurationError, defaultErrCtx)

		zbCmd := client.
			NewCancelInstanceCommand().
		    WorkflowInstanceKey(workflowInstanceKey)
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)

        _, err = zbCmd.Send()
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)
	},
}

func init() {
	cancelInstanceCmd.
		Flags().
		StringVar(&instancePayloadFlag, "payload", "{}", "Specify payload as JSON string")

	cancelInstanceCmd.
		Flags().
		Int32Var(&versionFlag, "version", zbCommands.LatestVersion,
			"Specify version of process which should be executed.")

	cancelCmd.AddCommand(cancelInstanceCmd)
}
