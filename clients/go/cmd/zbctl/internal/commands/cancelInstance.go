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
	"github.com/spf13/cobra"
	"github.com/camunda-cloud/zeebe/clients/go/pkg/pb"
)

type CancelInstanceResponseWrapper struct {
	resp *pb.CancelWorkflowInstanceResponse
}

func (c CancelInstanceResponseWrapper) human() (string, error) {
	return fmt.Sprint("Canceled workflow instance with key '", cancelInstanceKey, "'"), nil
}

func (c CancelInstanceResponseWrapper) json() (string, error) {
	return toJSON(c.resp)
}

var cancelInstanceKey int64

var cancelInstanceCmd = &cobra.Command{
	Use:     "instance <key>",
	Short:   "Cancel workflow instance by key",
	Args:    keyArg(&cancelInstanceKey),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		zbCmd := client.
			NewCancelInstanceCommand().
			WorkflowInstanceKey(cancelInstanceKey)

		ctx, cancel := context.WithTimeout(context.Background(), defaultTimeout)
		defer cancel()

		resp, err := zbCmd.Send(ctx)
		if err != nil {
			return err
		}
		err = printOutput(CancelInstanceResponseWrapper{resp})
		return err
	},
}

func init() {
	addOutputFlag(cancelInstanceCmd)
	cancelCmd.AddCommand(cancelInstanceCmd)
}
