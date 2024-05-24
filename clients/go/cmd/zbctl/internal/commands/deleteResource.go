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
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
	"github.com/spf13/cobra"
)

type DeleteResourceResponseWrapper struct {
	resp *pb.DeleteResourceResponse
}

func (c DeleteResourceResponseWrapper) json() (string, error) {
	return toJSON(c.resp)
}

func (c DeleteResourceResponseWrapper) human() (string, error) {
	return fmt.Sprint("Deleted resource with key '", resourceKey, "'"), nil
}

var resourceKey int64

var deleteResourceCmd = &cobra.Command{
	Use:     "resource <key>",
	Short:   "Delete resource by key",
	Args:    keyArg(&resourceKey),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		zbCmd := client.NewDeleteResourceCommand().ResourceKey(resourceKey)
		ctx, cancel := context.WithTimeout(context.Background(), timeoutFlag)
		defer cancel()

		resp, err := zbCmd.Send(ctx)
		if err != nil {
			return err
		}
		err = printOutput(DeleteResourceResponseWrapper{resp})
		return err
	},
}

func init() {
	addOutputFlag(deleteResourceCmd)
	deleteCmd.AddCommand(deleteResourceCmd)
}
