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
	"github.com/camunda/zeebe/clients/go/v8/internal/utils"
	"github.com/camunda/zeebe/clients/go/v8/pkg/commands"
	"github.com/spf13/cobra"
	"os"
	"strconv"
)

var (
	evaluateDecisionVariablesFlag string
)

var evaluateDecisionCmd = &cobra.Command{
	Use:     "decision <decisionId or decisionKey>",
	Short:   "Evaluates a decision defined by the decision ID or decision key",
	Args:    cobra.ExactArgs(1),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		var zbCmd commands.EvaluateDecisionCommandStep2

		decisionKey, err := strconv.Atoi(args[0])
		if err != nil {
			// Decision ID given
			zbCmd = client.NewEvaluateDecisionCommand().DecisionId(args[0])
		} else {
			zbCmd = client.NewEvaluateDecisionCommand().DecisionKey(int64(decisionKey))
		}

		decisionVariables, err := parseDecisionEvaluationVariables(evaluateDecisionVariablesFlag)
		if err != nil {
			return err
		}
		zbCmd, err = zbCmd.VariablesFromString(decisionVariables)

		if err != nil {
			return err
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
	evaluateCmd.AddCommand(evaluateDecisionCmd)

	evaluateDecisionCmd.
		Flags().
		StringVar(&evaluateDecisionVariablesFlag, "variables", "{}", "Specify variables as JSON string")
}

func parseDecisionEvaluationVariables(decisionVariables string) (string, error) {
	jsStringChecker := utils.NewJSONStringSerializer()
	jsonErr := jsStringChecker.Validate("variables", decisionVariables)
	if jsonErr != nil {
		// not a JSON string
		fileVariables, err := os.ReadFile(decisionVariables)
		if err != nil {
			// not a file path or valid JSON string
			return "", errors.New("invalid --variables passed. Invalid file or " + jsonErr.Error())
		}
		return string(fileVariables), nil
	}
	return decisionVariables, nil
}
