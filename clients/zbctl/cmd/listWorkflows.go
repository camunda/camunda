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
)

var listWorkflowsBpmnProcessIdFlag string

// listWorkflowsCmd represents the listWorkflows command
var listWorkflowsCmd = &cobra.Command{
	Use:   "workflows",
	Short: "List deployed workflows",
	Args: cobra.NoArgs,
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		response, err := client.NewListWorkflowsCommand().BpmnProcessId(listWorkflowsBpmnProcessIdFlag).Send()
		if err == nil && response.Workflows != nil {
			printJson(response.Workflows)
		}

		return err
	},
}

func init() {
	listCmd.AddCommand(listWorkflowsCmd)

	listWorkflowsCmd.Flags().StringVar(&listWorkflowsBpmnProcessIdFlag, "bpmnProcessId", "", "Specify the BPMN process id of the workflows to list")
}
