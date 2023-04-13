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
	"os"
)

var resourceNamesFlag []string

// Remove the nolint directive when this command is the only remaining one for deploy
// nolint
var deployResourceCmd = &cobra.Command{
	Use:     "resource <resourcePath>...",
	Short:   "Deploys a new resource (e.g. process, decision) for each BPMN/DMN resource provided",
	Args:    cobra.MinimumNArgs(1),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		if len(resourceNamesFlag) > len(args) {
			return fmt.Errorf("there are more resource names (%d) than resource paths (%d)", len(resourceNamesFlag), len(args))
		}

		zbCmd := client.NewDeployResourceCommand()
		for i := 0; i < len(resourceNamesFlag); i++ {
			bytes, err := os.ReadFile(args[i])
			if err != nil {
				return err
			}

			zbCmd.AddResource(bytes, resourceNamesFlag[i])
		}

		for i := len(resourceNamesFlag); i < len(args); i++ {
			zbCmd = zbCmd.AddResourceFile(args[i])
		}

		ctx, cancel := context.WithTimeout(context.Background(), timeoutFlag)
		defer cancel()

		response, err := zbCmd.Send(ctx)
		if err != nil {
			return err
		}

		return printJSON(response)
	},
}

func init() {
	deployCmd.AddCommand(deployResourceCmd)

	deployResourceCmd.Flags().StringSliceVar(&resourceNamesFlag, "resourceNames", nil, "Resource names"+
		" for the resource paths passed as arguments. The resource names are matched to resources by position. If a"+
		" resource does not have a matching resource name, the resource path is used instead")
}
