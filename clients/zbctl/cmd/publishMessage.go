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
	"time"
)

var (
	publishMessageCorrelationKey string
	publishMessageId             string
	publishMessageTtl            time.Duration
	publishMessageVariables      string
)

var publishMessageCmd = &cobra.Command{
	Use:     "message <messageName>",
	Short:   "Publish a message by message name and correlation key",
	Args:    cobra.ExactArgs(1),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		request, err := client.NewPublishMessageCommand().
			MessageName(args[0]).
			CorrelationKey(publishMessageCorrelationKey).
			MessageId(publishMessageId).
			TimeToLive(publishMessageTtl).
			VariablesFromString(publishMessageVariables)

		if err != nil {
			return err
		}

		_, err = request.Send()
		return err
	},
}

func init() {
	publishCmd.AddCommand(publishMessageCmd)

	publishMessageCmd.Flags().StringVar(&publishMessageCorrelationKey, "correlationKey", "", "Specify message correlation key")
	publishMessageCmd.Flags().StringVar(&publishMessageId, "messageId", "", "Specify the unique id of the message")
	publishMessageCmd.Flags().DurationVar(&publishMessageTtl, "ttl", 5*time.Second, "Specify the time to live of the message")
	publishMessageCmd.Flags().StringVar(&publishMessageVariables, "variables", "{}", "Specify message variables as JSON string")

	publishMessageCmd.MarkFlagRequired("correlationKey")
}
