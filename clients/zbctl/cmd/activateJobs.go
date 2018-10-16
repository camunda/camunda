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
	"github.com/zeebe-io/zeebe/clients/zbctl/utils"
	"log"
	"time"
)

var (
	activateJobsAmountFlag  int32
	activateJobsWorkerFlag  string
	activateJobsTimeoutFlag time.Duration
)

// activateJobsCmd represents the activateJob command
var activateJobsCmd = &cobra.Command{
	Use:    "jobs <type>",
	Short:  "Activate jobs for type",
	Args:   cobra.ExactArgs(1),
	PreRun: initBroker,
	Run: func(cmd *cobra.Command, args []string) {
		jobType := args[0]
		jobs, err := client.NewActivateJobsCommand().JobType(jobType).Amount(activateJobsAmountFlag).WorkerName(activateJobsWorkerFlag).Timeout(activateJobsTimeoutFlag).Send()
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)

		jobsCount := len(jobs)
		if jobsCount > 0 {
			log.Println("Activated", jobsCount, "for type", jobType)
			for index, job := range jobs {
				log.Println("Job", index+1, "/", jobsCount)
				out.Serialize(job).Flush()
			}
		} else {
			log.Println("No jobs found to activate for type", jobType)
		}
	},
}

func init() {
	activateCmd.AddCommand(activateJobsCmd)
	activateJobsCmd.Flags().Int32Var(&activateJobsAmountFlag, "amount", 1, "Specify amount of jobs to activate")
	activateJobsCmd.Flags().StringVar(&activateJobsWorkerFlag, "worker", utils.DefaultJobWorker, "Specify the name of the worker")
	activateJobsCmd.Flags().DurationVar(&activateJobsTimeoutFlag, "timeout", utils.DefaultJobTimeout, "Specify the timeout of the activated job")
}
