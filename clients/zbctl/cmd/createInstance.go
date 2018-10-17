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
	createInstanceVersionFlag int32
	createInstancePayloadFlag string
)

var createInstanceCmd = &cobra.Command{
	Use:    "instance <processId>",
	Short:  "Creates new workflow instance defined by the process ID",
	Args:   cobra.ExactArgs(1),
	PreRun: initClient,
	Run: func(cmd *cobra.Command, args []string) {
		zbCmd, err := client.
			NewCreateInstanceCommand().
			BPMNProcessId(args[0]).
			Version(createInstanceVersionFlag).
			PayloadFromString(createInstancePayloadFlag)
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)

		response, err := zbCmd.Send()
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)

		out.Serialize(response).Flush()
	},
}

func init() {
	createCmd.AddCommand(createInstanceCmd)

	createInstanceCmd.
		Flags().
		StringVar(&createInstancePayloadFlag, "payload", utils.EmptyJsonObject, "Specify payload as JSON string")

	createInstanceCmd.
		Flags().
		Int32Var(&createInstanceVersionFlag, "version", utils.LatestVersion,
			"Specify version of workflow which should be executed.")
}
