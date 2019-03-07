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
	"log"
)

var (
	setVariablesKey           int64
	setVariablesVariablesFlag string
	setVariablesLocalFlag     bool
)

var setVariablesCmd = &cobra.Command{
	Use:     "variables <key>",
	Short:   "Sets the variables of a given flow element",
	Args:    keyArg(&setVariablesKey),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		request, err := client.NewSetVariablesCommand().ElementInstanceKey(setVariablesKey).VariablesFromString(setVariablesVariablesFlag)
		if err != nil {
			return err
		}

		request.Local(setVariablesLocalFlag)
		_, err = request.Send()
		if err == nil {
			log.Println("Set the variables of element instance with key", setVariablesKey, "to", setVariablesVariablesFlag)
		}

		return err
	},
}

func init() {
	setCmd.AddCommand(setVariablesCmd)

	setVariablesCmd.Flags().StringVar(&setVariablesVariablesFlag, "variables", "{}", "Specify the variables as JSON object string")
	setVariablesCmd.MarkFlagRequired("variables")

	setVariablesCmd.Flags().BoolVar(&setVariablesLocalFlag, "local", false, "Specify local or propagating update semantics")
}
