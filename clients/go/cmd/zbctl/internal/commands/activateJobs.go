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
	"github.com/zeebe-io/zeebe/clients/go/pkg/commands"
	"time"
)

const (
	DefaultJobWorkerName = "zbctl"
)

var (
	maxJobsToActivateFlag          int32
	activateJobsWorkerFlag         string
	activateJobsTimeoutFlag        time.Duration
	activateJobsFetchVariablesFlag []string
	activateJobsRequestTimeoutFlag time.Duration
)

var activateJobsCmd = &cobra.Command{
	Use:     "jobs <type>",
	Short:   "Activate jobs for type",
	Args:    cobra.ExactArgs(1),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		timeout := defaultTimeout

		if activateJobsRequestTimeoutFlag != 0 {
			timeout = activateJobsRequestTimeoutFlag
		}

		ctx, cancel := context.WithTimeout(context.Background(), timeout)
		defer cancel()

		jobType := args[0]
		jobs, err := client.NewActivateJobsCommand().
			JobType(jobType).
			MaxJobsToActivate(maxJobsToActivateFlag).
			WorkerName(activateJobsWorkerFlag).
			Timeout(activateJobsTimeoutFlag).
			FetchVariables(activateJobsFetchVariablesFlag...).
			Send(ctx)
		if err != nil {
			return err
		}

		jobsCount := len(jobs)
		if jobsCount > 0 {
			if err := printJSON(jobs); err != nil {
				return err
			}
		} else {
			fmt.Println("[]")
		}

		return nil
	},
}

func init() {
	activateCmd.AddCommand(activateJobsCmd)
	activateJobsCmd.Flags().Int32Var(&maxJobsToActivateFlag, "maxJobsToActivate", 1, "Specify the maximum amount of jobs to activate")
	activateJobsCmd.Flags().StringVar(&activateJobsWorkerFlag, "worker", DefaultJobWorkerName, "Specify the name of the worker")
	activateJobsCmd.Flags().DurationVar(&activateJobsTimeoutFlag, "timeout", commands.DefaultJobTimeout, "Specify the timeout of the activated job")
	activateJobsCmd.Flags().StringSliceVar(&activateJobsFetchVariablesFlag, "variables", []string{}, "Specify the list of variable names which should be fetch on job activation (comma-separated)")
	activateJobsCmd.Flags().DurationVar(&activateJobsRequestTimeoutFlag, "requestTimeout", 0, "Specify the request timeout")
}
