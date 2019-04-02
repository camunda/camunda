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
)

var activateJobsCmd = &cobra.Command{
    Use:     "jobs <type>",
    Short:   "Activate jobs for type",
    Args:    cobra.ExactArgs(1),
    PreRunE: initClient,
    RunE: func(cmd *cobra.Command, args []string) error {
        jobType := args[0]
        jobs, err := client.NewActivateJobsCommand().JobType(jobType).MaxJobsToActivate(maxJobsToActivateFlag).WorkerName(activateJobsWorkerFlag).Timeout(activateJobsTimeoutFlag).FetchVariables(activateJobsFetchVariablesFlag...).Send()
        if err != nil {
            return err
        }

        jobsCount := len(jobs)
        if jobsCount > 0 {
            log.Println("Activated", jobsCount, "for type", jobType)
            for index, job := range jobs {
                log.Println("Job", index+1, "/", jobsCount)
                printJson(job)
            }
        } else {
            log.Println("No jobs found to activate for type", jobType)
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
}
