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
	"github.com/camunda/zeebe/clients/go/pkg/commands"
	"github.com/camunda/zeebe/clients/go/pkg/pb"
	"github.com/spf13/cobra"
)

var (
	updateRetriesKey  int64
	updateRetriesFlag int32
)

type UpdateJobRetriesResponseWrapper struct {
	response *pb.UpdateJobRetriesResponse
}

func (u UpdateJobRetriesResponseWrapper) human() (string, error) {
	return fmt.Sprint("Updated the retries of job with key '", updateRetriesKey, "' to '", updateRetriesFlag, "'"), nil
}

func (u UpdateJobRetriesResponseWrapper) json() (string, error) {
	return toJSON(u.response)
}

var updateRetriesCmd = &cobra.Command{
	Use:     "retries <key>",
	Short:   "Update retries of a job",
	Args:    keyArg(&updateRetriesKey),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		ctx, cancel := context.WithTimeout(context.Background(), timeoutFlag)
		defer cancel()

		resp, err := client.NewUpdateJobRetriesCommand().JobKey(updateRetriesKey).Retries(updateRetriesFlag).Send(ctx)
		if err != nil {
			return err
		}
		err = printOutput(UpdateJobRetriesResponseWrapper{resp})

		return err
	},
}

func init() {
	addOutputFlag(updateRetriesCmd)
	updateCmd.AddCommand(updateRetriesCmd)
	updateRetriesCmd.Flags().Int32Var(&updateRetriesFlag, "retries", commands.DefaultJobRetries, "Specify retries of job")
	if err := updateRetriesCmd.MarkFlagRequired("retries"); err != nil {
		panic(err)
	}
}
