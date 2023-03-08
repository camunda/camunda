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
	"errors"
	"io/ioutil"

	"github.com/camunda/zeebe/clients/go/v8/internal/utils"
	"github.com/spf13/cobra"
)

var (
	broadcastSignalVariables string
)

var broadcastSignalCmd = &cobra.Command{
	Use:     "signal <signalName>",
	Short:   "Broadcast a signal by signal name",
	Args:    cobra.ExactArgs(1),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		parsedSignalVariables, err := parseBroadcastSignalVariables(broadcastSignalVariables)
		if err != nil {
			return err
		}

		request, err := client.NewBroadcastSignalCommand().
			SignalName(args[0]).
			VariablesFromString(parsedSignalVariables)

		if err != nil {
			return err
		}

		ctx, cancel := context.WithTimeout(context.Background(), timeoutFlag)
		defer cancel()

		response, err := request.Send(ctx)
		if err != nil {
			return err
		}

		return printJSON(response)
	},
}

func init() {
	broadcastCmd.AddCommand(broadcastSignalCmd)

	broadcastSignalCmd.Flags().StringVar(&broadcastSignalVariables, "variables", "{}", "Specify signal variables as JSON string or path to JSON file")
}

func parseBroadcastSignalVariables(variables string) (string, error) {
	jsStringChecker := utils.NewJSONStringSerializer()
	jsonErr := jsStringChecker.Validate("variables", variables)
	if jsonErr != nil {
		// not a JSON string
		fileVariables, err := ioutil.ReadFile(variables)
		if err != nil {
			// not a file path or valid JSON string
			return "", errors.New("invalid --variables passed. Invalid file or " + jsonErr.Error())
		}
		return string(fileVariables), nil
	}
	return variables, nil
}
