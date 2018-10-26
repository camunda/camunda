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
	"github.com/spf13/cobra"
	"github.com/zeebe-io/zeebe/clients/go/commands"
	"github.com/zeebe-io/zeebe/clients/zbctl/utils"
	"os"
)

var (
	getWorkflowKeyFlag           int64
	getWorkflowBpmnProcessIdFlag string
	getWorkflowVersionFlag       int32
)

// getWorkflowCmd represents the getWorkflow command
var getWorkflowCmd = &cobra.Command{
	Use:   "workflow",
	Short: "Get a workflow resource",
	Args: cobra.NoArgs,
	PreRun: initClient,
	Run: func(cmd *cobra.Command, args []string) {
		if getWorkflowKeyFlag < 1 && len(getWorkflowBpmnProcessIdFlag) == 0 {
			fmt.Println("Either workflow key or BPMN process id has to be specified")
			os.Exit(utils.ExitCodeConfigurationError)
		}

		if getWorkflowKeyFlag > 0 && getWorkflowVersionFlag > utils.LatestVersion {
			fmt.Println("No version allowed when workflow key is specified, got key", getWorkflowKeyFlag, "and version", getWorkflowVersionFlag)
			os.Exit(utils.ExitCodeConfigurationError)
		}

		var request commands.DispatchGetWorkflowCommand

		if getWorkflowKeyFlag > 0 {
			request = client.NewGetWorkflowCommand().WorkflowKey(getWorkflowKeyFlag)
		} else {
			request = client.NewGetWorkflowCommand().BpmnProcessId(getWorkflowBpmnProcessIdFlag).Version(getWorkflowVersionFlag)
		}

		response, err := request.Send()
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)

		out.Serialize(response).Flush()
	},
}

func init() {
	getCmd.AddCommand(getWorkflowCmd)

	getWorkflowCmd.Flags().Int64Var(&getWorkflowKeyFlag, "workflowKey", 0, "Specify workflow key")
	getWorkflowCmd.Flags().StringVar(&getWorkflowBpmnProcessIdFlag, "bpmnProcessId", "", "Specify BPMN process id of workflow")
	getWorkflowCmd.Flags().Int32Var(&getWorkflowVersionFlag, "version", utils.LatestVersion, "Specify workflow version if BPMN process id is specified")
}
