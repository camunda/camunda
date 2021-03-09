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
package commands

import (
	"context"
	"fmt"
	"github.com/spf13/cobra"
	"github.com/camunda-cloud/zeebe/clients/go/pkg/pb"
	"io/ioutil"
)

var resourceNamesFlag []string

var deployWorkflowCmd = &cobra.Command{
	Use:     "deploy <workflowPath>...",
	Short:   "Creates a new workflow for each BPMN or YAML resource provided",
	Args:    cobra.MinimumNArgs(1),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		if len(resourceNamesFlag) > len(args) {
			return fmt.Errorf("there are more resource names (%d) than workflow paths (%d)", len(resourceNamesFlag), len(args))
		}

		zbCmd := client.NewDeployWorkflowCommand()
		for i := 0; i < len(resourceNamesFlag); i++ {
			bytes, err := ioutil.ReadFile(args[i])
			if err != nil {
				return err
			}

			zbCmd.AddResource(bytes, resourceNamesFlag[i], pb.WorkflowRequestObject_FILE)
		}

		for i := len(resourceNamesFlag); i < len(args); i++ {
			zbCmd = zbCmd.AddResourceFile(args[i])
		}

		ctx, cancel := context.WithTimeout(context.Background(), defaultTimeout)
		defer cancel()

		response, err := zbCmd.Send(ctx)
		if err != nil {
			return err
		}

		return printJSON(response)
	},
}

func init() {
	rootCmd.AddCommand(deployWorkflowCmd)

	deployWorkflowCmd.Flags().StringSliceVar(&resourceNamesFlag, "resourceNames", nil, "Resource names"+
		" for the workflows paths passed as arguments. The resource names are matched to workflows by position. If a"+
		" workflow does not have a matching resource name, the workflow path is used instead")
}
