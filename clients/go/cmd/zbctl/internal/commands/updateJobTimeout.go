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

// nolint
package commands

import (
	"context"
	"fmt"
	"github.com/camunda/zeebe/clients/go/v8/pkg/commands"
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
	"github.com/spf13/cobra"
)

var (
	updateTimeoutKey  int64
	updateTimeoutFlag int64
)

type UpdateJobTimeoutResponseWrapper struct {
	response *pb.UpdateJobTimeoutResponse
}

func (u UpdateJobTimeoutResponseWrapper) human() (string, error) {
	return fmt.Sprint("Updated the timeout of job with key '", updateTimeoutKey, "' to '", updateTimeoutFlag, "'"), nil
}

func (u UpdateJobTimeoutResponseWrapper) json() (string, error) {
	return toJSON(u.response)
}

var updateTimeoutCmd = &cobra.Command{
	Use:     "timeout <key>",
	Short:   "Update timeout of a job",
	Args:    keyArg(&updateTimeoutKey),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		ctx, cancel := context.WithTimeout(cmd.Context(), timeoutFlag)
		defer cancel()

		resp, err := client.NewUpdateJobTimeoutCommand().JobKey(updateTimeoutKey).Timeout(updateTimeoutFlag).Send(ctx)
		if err != nil {
			return err
		}
		err = printOutput(UpdateJobTimeoutResponseWrapper{resp})

		return err
	},
}

func init() {
	addOutputFlag(updateTimeoutCmd)
	updateCmd.AddCommand(updateTimeoutCmd)
	updateTimeoutCmd.Flags().Int64Var(&updateTimeoutFlag, "timeout", commands.DefaultJobTimeoutInMs, "Specify timeout of job")
	if err := updateTimeoutCmd.MarkFlagRequired("timeout"); err != nil {
		panic(err)
	}
}
