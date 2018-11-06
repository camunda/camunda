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
	"github.com/zeebe-io/zeebe/clients/go/commands"
)

var (
	createJobRetriesFlag       int32
	createJobCustomHeadersFlag string
	createJobPayloadFlag       string
)

var createJobCmd = &cobra.Command{
	Use:     "job <type>",
	Short:   "Creates a new job with specified type",
	Args:    cobra.ExactArgs(1),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		zbCmd, err := client.
			NewCreateJobCommand().
			JobType(args[0]).
			Retries(createJobRetriesFlag).
			SetCustomHeadersFromString(createJobCustomHeadersFlag)
		if err != nil {
			return err
		}

		zbCmd, err = zbCmd.PayloadFromString(createJobPayloadFlag)
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
	createCmd.AddCommand(createJobCmd)

	createJobCmd.Flags().Int32Var(&createJobRetriesFlag, "retries", commands.DefaultJobRetries, "Specify retries of job")

	createJobCmd.
		Flags().
		StringVar(&createJobCustomHeadersFlag, "headers", "{}", "Specify custom headers as JSON string")

	createJobCmd.
		Flags().
		StringVar(&createJobPayloadFlag, "payload", "{}", "Specify payload as JSON string")
}
