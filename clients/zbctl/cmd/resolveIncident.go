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
	"log"
)

var (
	incidentKey int64
)

var resolveIncidentCommand = &cobra.Command{
	Use:     "incident <key>",
	Short:   "Resolve an existing incident of a workflow instance",
	Args:    keyArg(&incidentKey),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		_, err := client.NewResolveIncidentCommand().IncidentKey(incidentKey).Send()
		if err == nil {
			log.Println("Resolved an incident of a workflow instance with key", incidentKey)
		}

		return err
	},
}

func init() {
	resolveCmd.AddCommand(resolveIncidentCommand)
}
