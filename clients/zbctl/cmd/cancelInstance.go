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

var cancelInstanceCmd = &cobra.Command{
	Use:    "instance <key>",
	Short:  "Cancel workflow instance by key",
	Args:   cobra.ExactArgs(1),
	PreRun: initClient,
	Run: func(cmd *cobra.Command, args []string) {
		workflowInstanceKey := convertToKey(args[0], "Expect workflow instance key as only positional argument, got")

		zbCmd := client.
			NewCancelInstanceCommand().
			WorkflowInstanceKey(workflowInstanceKey)

		_, err := zbCmd.Send()
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)
	},
}

func init() {
	cancelCmd.AddCommand(cancelInstanceCmd)
}
