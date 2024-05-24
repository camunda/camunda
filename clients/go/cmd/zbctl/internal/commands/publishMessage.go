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
	"os"
	"time"

	"errors"
	"github.com/camunda/zeebe/clients/go/v8/internal/utils"
	"github.com/spf13/cobra"
)

var (
	publishMessageCorrelationKey string
	publishMessageID             string
	publishMessageTTL            time.Duration
	publishMessageVariables      string
)

var publishMessageCmd = &cobra.Command{
	Use:     "message <messageName>",
	Short:   "Publish a message by message name and correlation key",
	Args:    cobra.ExactArgs(1),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		parsedMessageVariables, err := parsePublishMessageVariables(publishMessageVariables)
		if err != nil {
			return err
		}

		request, err := client.NewPublishMessageCommand().
			MessageName(args[0]).
			CorrelationKey(publishMessageCorrelationKey).
			MessageId(publishMessageID).
			TimeToLive(publishMessageTTL).
			VariablesFromString(parsedMessageVariables)

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
	publishCmd.AddCommand(publishMessageCmd)

	publishMessageCmd.Flags().StringVar(&publishMessageCorrelationKey, "correlationKey", "", "Specify message correlation key")
	publishMessageCmd.Flags().StringVar(&publishMessageID, "messageId", "", "Specify the unique id of the message")
	publishMessageCmd.Flags().DurationVar(&publishMessageTTL, "ttl", 5*time.Second, "Specify the time to live of the message. Example values: 300ms, 50s or 1m")
	publishMessageCmd.Flags().StringVar(&publishMessageVariables, "variables", "{}", "Specify message variables as JSON string or path to JSON file")
	if err := publishMessageCmd.MarkFlagRequired("correlationKey"); err != nil {
		panic(err)
	}
}

func parsePublishMessageVariables(messageVariables string) (string, error) {
	jsStringChecker := utils.NewJSONStringSerializer()
	jsonErr := jsStringChecker.Validate("variables", messageVariables)
	if jsonErr != nil {
		// not a JSON string
		fileVariables, err := os.ReadFile(messageVariables)
		if err != nil {
			// not a file path or valid JSON string
			return "", errors.New("invalid --variables passed. Invalid file or " + jsonErr.Error())
		}
		return string(fileVariables), nil
	}
	return messageVariables, nil
}
