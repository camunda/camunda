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
	zbCommands "github.com/zeebe-io/zeebe/clients/go/commands"
)

var (
	versionFlag         int32
	instancePayloadFlag string
)

var createInstanceCmd = &cobra.Command{
	Use:   "instance <processId>",
	Short: "Creates new workflow instance defined by the process ID",
	Long:  ``,
	PreRun: func(cmd *cobra.Command, args []string) {
		initBroker(cmd)
	},
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) == 0 {
			fmt.Println("You must specify process ID as positional arguments.")
			os.Exit(utils.ExitCodeConfigurationError)
		}

		client, err := zbc.NewZBClient(brokerAddr)
		utils.CheckOrExit(err, utils.ExitCodeConfigurationError, defaultErrCtx)

		zbCmd, err := client.
			NewCreateInstanceCommand().
			BPMNProcessId(args[0]).
			Version(versionFlag).
			PayloadFromString(instancePayloadFlag)
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)

		response, err := zbCmd.Send()
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)

		out.Serialize(response).Flush()
	},
}

func init() {
	createInstanceCmd.
		Flags().
		StringVar(&instancePayloadFlag, "payload", "{}", "Specify payload as JSON string")

	createInstanceCmd.
		Flags().
		Int32Var(&versionFlag, "version", zbCommands.LatestVersion,
			"Specify version of process which should be executed.")

	createCmd.AddCommand(createInstanceCmd)
}
