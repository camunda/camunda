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
	"github.com/zeebe-io/zeebe/clients/go/commands"

	"github.com/spf13/cobra"
)

var (
	createInstanceVersionFlag   int32
	createInstanceVariablesFlag string
)

var createInstanceCmd = &cobra.Command{
	Use:     "instance <processId>",
	Short:   "Creates new workflow instance defined by the process ID",
	Args:    cobra.ExactArgs(1),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		zbCmd, err := client.
			NewCreateInstanceCommand().
			BPMNProcessId(args[0]).
			Version(createInstanceVersionFlag).
			VariablesFromString(createInstanceVariablesFlag)
		if err != nil {
			return err
		}

		response, err := zbCmd.Send()
		if err != nil {
			return err
		}

		return printJson(response)
	},
}

func init() {
	createCmd.AddCommand(createInstanceCmd)

	createInstanceCmd.
		Flags().
		StringVar(&createInstanceVariablesFlag, "variables", "{}", "Specify variables as JSON string")

	createInstanceCmd.
		Flags().
		Int32Var(&createInstanceVersionFlag, "version", commands.LatestVersion, "Specify version of workflow which should be executed.")
}
