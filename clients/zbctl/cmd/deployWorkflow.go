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

var deployWorkflowCmd = &cobra.Command{
	Use:     "deploy <workflowPath>",
	Short:   "Creates new workflow defined by provided BPMN or YAML file as workflowPath",
	Args:    cobra.MinimumNArgs(1),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		zbCmd := client.NewDeployWorkflowCommand().AddResourceFile(args[0])
		for i := 1; i < len(args); i++ {
			zbCmd = zbCmd.AddResourceFile(args[i])
		}

		response, err := zbCmd.Send()
		if err != nil {
			return err
		}

		return printJson(response)
	},
}

func init() {
	rootCmd.AddCommand(deployWorkflowCmd)
}
