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
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		if getWorkflowKeyFlag < 1 && len(getWorkflowBpmnProcessIdFlag) == 0 {
			return fmt.Errorf("either workflow key or BPMN process id has to be specified")
		}

		if getWorkflowKeyFlag > 0 && len(getWorkflowBpmnProcessIdFlag) > 0 {
			return fmt.Errorf("only one of workflow key or BPMN process id can be specified, got key %d and BPMN process id %q", getWorkflowKeyFlag, getWorkflowBpmnProcessIdFlag)
		}

		if getWorkflowKeyFlag > 0 && getWorkflowVersionFlag > commands.LatestVersion {
			return fmt.Errorf("no version allowed when workflow key is specified, got key %d and verion %d", getWorkflowKeyFlag, getWorkflowVersionFlag)
		}

		var request commands.DispatchGetWorkflowCommand

		if getWorkflowKeyFlag > 0 {
			request = client.NewGetWorkflowCommand().WorkflowKey(getWorkflowKeyFlag)
		} else {
			request = client.NewGetWorkflowCommand().BpmnProcessId(getWorkflowBpmnProcessIdFlag).Version(getWorkflowVersionFlag)
		}

		response, err := request.Send()
		if err == nil {
			printJson(response)
		}

		return err
	},
}

func init() {
	getCmd.AddCommand(getWorkflowCmd)

	getWorkflowCmd.Flags().Int64Var(&getWorkflowKeyFlag, "workflowKey", 0, "Specify workflow key")
	getWorkflowCmd.Flags().StringVar(&getWorkflowBpmnProcessIdFlag, "bpmnProcessId", "", "Specify BPMN process id of workflow")
	getWorkflowCmd.Flags().Int32Var(&getWorkflowVersionFlag, "version", commands.LatestVersion, "Specify workflow version if BPMN process id is specified")
}
