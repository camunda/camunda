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
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"github.com/spf13/cobra"
	"github.com/zeebe-io/zeebe/clients/go/pkg/entities"
	"github.com/zeebe-io/zeebe/clients/go/pkg/worker"
	"log"
	"time"
)

type jobWorkerMetricsImpl struct {
	name string
}

var (
	workerJobTypeFlag         string
	workerNameFlag            string
	workerConcurrencyFlag     int
	workerCapacityFlag        int
	workerPollingDelayFlag    time.Duration
	workerCompletionDelayFlag time.Duration
	workerJobTimeoutFlag      time.Duration
	workerJobMetrics          = jobWorkerMetricsImpl{name: "zbench"}
	workerRemainingJobsCount  = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Namespace: "zeebe",
		Name:      "jobs_remaining_count",
		Help:      "The total number of processed events",
	}, []string{"worker", "jobType"})
)

var workerCmd = &cobra.Command{
	Use:     "worker",
	Short:   "Completes job of a specific job type at a given interval",
	Args:    cobra.ExactArgs(0),
	PreRunE: initClient,
	Run: func(cmd *cobra.Command, args []string) {
		jobWorker := client.
			NewJobWorker().
			JobType(workerJobTypeFlag).
			Handler(func(jobClient worker.JobClient, job entities.Job) {
				ctx, cancelFunc := context.WithTimeout(cmd.Context(), DefaultTimeout)
				defer cancelFunc()

				time.Sleep(workerCompletionDelayFlag)
				if _, err := jobClient.NewCompleteJobCommand().JobKey(job.GetKey()).Send(ctx); err != nil {
					log.Printf("Failed to complete job with key %d, %s", job.GetKey(), err)
				}
			}).
			Concurrency(workerConcurrencyFlag).
			MaxJobsActive(workerCapacityFlag).
			PollInterval(workerPollingDelayFlag).
			RequestTimeout(DefaultTimeout).
			Timeout(workerJobTimeoutFlag).
			Metrics(workerJobMetrics).
			Open()

		jobWorker.AwaitClose()
	},
}

func init() {
	rootCmd.AddCommand(workerCmd)

	workerCmd.
		Flags().
		IntVar(&workerCapacityFlag, "capacity", 100, "Specify the maximum number of jobs a user can activate at once")

	workerCmd.
		Flags().
		DurationVar(&workerCompletionDelayFlag, "completionDelay", 100*time.Millisecond, "Specify a delay in milliseconds before the job is completed")

	workerCmd.
		Flags().
		DurationVar(&workerPollingDelayFlag, "pollingDelay", 100*time.Millisecond, "Specify a delay in milliseconds before the job poller polls again")

	workerCmd.
		Flags().
		IntVar(&workerConcurrencyFlag, "concurrency", 100, "Specify the maximum number of concurrent spawned goroutines to complete jobs")

	workerCmd.
		Flags().
		DurationVar(&workerJobTimeoutFlag, "timeout", 10*time.Second, "Specify the time before a job is made activatable again")

	workerCmd.
		Flags().
		StringVar(&workerJobTypeFlag, "jobType", "benchmark-task", "Specify the task type the worker should poll for")

	workerCmd.
		Flags().
		StringVar(&workerNameFlag, "name", "worker", "Specify the worker name, mostly for debugging purposes")
}

func (m jobWorkerMetricsImpl) SetJobsRemainingCount(jobType string, count int) {
	workerRemainingJobsCount.
		With(prometheus.Labels{"worker": m.name, "jobType": jobType}).
		Set(float64(count))
}
