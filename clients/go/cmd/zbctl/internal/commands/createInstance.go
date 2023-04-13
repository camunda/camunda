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
	"github.com/camunda/zeebe/clients/go/v8/pkg/commands"
	"github.com/spf13/cobra"
	"strconv"
	"strings"
)

var (
	createInstanceVersionFlag    int32
	createInstanceVariablesFlag  string
	createInstanceWithResultFlag []string
)

var createInstanceCmd = &cobra.Command{
	Use:     "instance <processId or processKey>",
	Short:   "Creates new process instance defined by the process ID or process key",
	Args:    cobra.ExactArgs(1),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		var zbCmd commands.CreateInstanceCommandStep3

		processKey, err := strconv.Atoi(args[0])
		if err != nil {
			// Process ID given
			zbCmd = client.NewCreateInstanceCommand().
				BPMNProcessId(args[0]).
				Version(createInstanceVersionFlag)
		} else {
			zbCmd = client.NewCreateInstanceCommand().
				ProcessDefinitionKey(int64(processKey))
		}

		zbCmd, err = zbCmd.VariablesFromString(createInstanceVariablesFlag)

		if err != nil {
			return err
		}

		ctx, cancel := context.WithTimeout(context.Background(), timeoutFlag)
		defer cancel()

		if createInstanceWithResultFlag == nil {
			response, err := zbCmd.Send(ctx)
			if err != nil {
				return err
			}

			return printJSON(response)
		}

		variableNames := []string{}
		for _, variableName := range createInstanceWithResultFlag {
			trimmedVariableName := strings.TrimSpace(variableName)
			if trimmedVariableName != "" {
				variableNames = append(variableNames, trimmedVariableName)
			}
		}
		response, err := zbCmd.WithResult().FetchVariables(variableNames...).Send(ctx)
		if err != nil {
			return err
		}

		return printJSON(response)
	},
}

func init() {
	createCmd.AddCommand(createInstanceCmd)

	createInstanceCmd.
		Flags().
		StringVar(&createInstanceVariablesFlag, "variables", "{}", "Specify variables as JSON string")

	createInstanceCmd.
		Flags().
		Int32Var(&createInstanceVersionFlag, "version", commands.LatestVersion, "Specify version of process which should be executed.")

	createInstanceCmd.
		Flags().
		StringSliceVar(&createInstanceWithResultFlag, "withResult", nil, "Specify to await result of process, optional a list of variable names can be provided to limit the returned variables")

	// hack to use --withResult without values
	createInstanceCmd.Flag("withResult").NoOptDefVal = " "
}
