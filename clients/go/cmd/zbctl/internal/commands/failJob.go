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
	"github.com/camunda/zeebe/clients/go/v8/pkg/commands"
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
	"github.com/spf13/cobra"
	"time"
)

type FailJobResponseWrapper struct {
	resp *pb.FailJobResponse
}

func (f FailJobResponseWrapper) human() (string, error) {
	return fmt.Sprint("Failed job with key '", failJobKey, "' and set remaining retries to '", failJobRetriesFlag, "' with retry backoff '", failJobRetryBackoffFlag, "'"), nil
}

func (f FailJobResponseWrapper) json() (string, error) {
	return toJSON(f.resp)
}

var (
	failJobKey              int64
	failJobRetriesFlag      int32
	failJobRetryBackoffFlag time.Duration
	failJobErrorMessage     string
)

var failJobCmd = &cobra.Command{
	Use:     "job <key>",
	Short:   "Fail a job",
	Args:    keyArg(&failJobKey),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		ctx, cancel := context.WithTimeout(context.Background(), timeoutFlag)
		defer cancel()

		resp, err := client.NewFailJobCommand().
			JobKey(failJobKey).
			Retries(failJobRetriesFlag).
			RetryBackoff(failJobRetryBackoffFlag).
			ErrorMessage(failJobErrorMessage).
			TenantId(tenantIdFlag).
			Send(ctx)
		if err != nil {
			return err
		}
		err = printOutput(FailJobResponseWrapper{resp})
		return err
	},
}

func init() {
	addOutputFlag(failJobCmd)
	failCmd.AddCommand(failJobCmd)
	failJobCmd.Flags().Int32Var(&failJobRetriesFlag, "retries", commands.DefaultJobRetries, "Specify remaining retries of job")
	if err := failJobCmd.MarkFlagRequired("retries"); err != nil {
		panic(err)
	}
	failJobCmd.Flags().DurationVar(&failJobRetryBackoffFlag, "retryBackoff", time.Second*0, "Specify retry backoff of job. Example values: 300ms, 50s or 1m")

	failJobCmd.Flags().StringVar(&failJobErrorMessage, "errorMessage", "", "Specify failure error message")

}
