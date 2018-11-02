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
	"log"
)

var (
	updateRetriesKey  int64
	updateRetriesFlag int32
)

var updateRetriesCmd = &cobra.Command{
	Use:     "retries <key>",
	Short:   "Update retries of a job",
	Args:    keyArg(&updateRetriesKey),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		_, err := client.NewUpdateJobRetriesCommand().JobKey(updateRetriesKey).Retries(updateRetriesFlag).Send()
		if err == nil {
			log.Println("Updated the retries of job with key", updateRetriesKey, "to", updateRetriesFlag)
		}

		return err
	},
}

func init() {
	updateCmd.AddCommand(updateRetriesCmd)
	updateRetriesCmd.Flags().Int32Var(&updateRetriesFlag, "retries", commands.DefaultJobRetries, "Specify retries of job")
	updateRetriesCmd.MarkFlagRequired("retries")
}
