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
	"github.com/camunda-cloud/zeebe/clients/go/pkg/pb"
	"github.com/spf13/cobra"
)

var (
	jobKey       int64
	errorCode    string
	errorMessage string
)

type ThrowErrorResponseWrapper struct {
	resp *pb.ThrowErrorResponse
}

func (t ThrowErrorResponseWrapper) human() (string, error) {
	return fmt.Sprintf("Threw error with code '%s' on job with key %d", errorCode, jobKey), nil
}

func (t ThrowErrorResponseWrapper) json() (string, error) {
	return toJSON(t.resp)
}

var throwErrorJobCmd = &cobra.Command{
	Use:     "job <jobKey>",
	Short:   "Throw a non-technical error from an active job",
	Args:    keyArg(&jobKey),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		ctx, cancel := context.WithTimeout(context.Background(), timeoutFlag)
		defer cancel()

		resp, err := client.NewThrowErrorCommand().JobKey(jobKey).ErrorCode(errorCode).ErrorMessage(errorMessage).Send(ctx)
		if err != nil {
			return err
		}

		err = printOutput(ThrowErrorResponseWrapper{resp})
		return err
	},
}

func init() {
	addOutputFlag(throwErrorJobCmd)
	throwErrorCmd.AddCommand(throwErrorJobCmd)
	throwErrorJobCmd.Flags().StringVar(&errorCode, "errorCode", "", "Specify an error code to which the error should be matched")
	if err := throwErrorJobCmd.MarkFlagRequired("errorCode"); err != nil {
		panic(err)
	}
	throwErrorJobCmd.Flags().StringVar(&errorMessage, "errorMessage", "", "Specify an error message with additional context")
}
