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

type ResolveIncidentResponseWrapper struct {
	resp *pb.ResolveIncidentResponse
}

func (r ResolveIncidentResponseWrapper) human() (string, error) {
	return fmt.Sprint("Resolved an incident of a process instance with key '", incidentKey, "'"), nil
}

func (r ResolveIncidentResponseWrapper) json() (string, error) {
	return toJSON(r.resp)
}

var (
	incidentKey int64
)

var resolveIncidentCommand = &cobra.Command{
	Use:     "incident <key>",
	Short:   "Resolve an existing incident of a process instance",
	Args:    keyArg(&incidentKey),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		ctx, cancel := context.WithTimeout(context.Background(), timeoutFlag)
		defer cancel()

		resp, err := client.NewResolveIncidentCommand().IncidentKey(incidentKey).Send(ctx)
		if err != nil {
			return err
		}
		err = printOutput(ResolveIncidentResponseWrapper{resp})
		return err
	},
}

func init() {
	addOutputFlag(resolveIncidentCommand)
	resolveCmd.AddCommand(resolveIncidentCommand)
}
