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
    "bytes"
    "fmt"
    "github.com/spf13/cobra"
    "github.com/zeebe-io/zeebe/clients/go/commands"
    "github.com/zeebe-io/zeebe/clients/go/entities"
    "github.com/zeebe-io/zeebe/clients/go/worker"
    "io"
    "log"
    "os/exec"
    "strings"
    "time"
)

var (
    createWorkerHandlerFlag       string
    createWorkerNameFlag          string
    createWorkerTimeoutFlag       time.Duration
    createWorkerMaxJobsActiveFlag int
    createWorkerConcurrencyFlag   int
    createWorkerPollIntervalFlag  time.Duration
    createWorkerPollThresholdFlag float64

    createWorkerHandlerArgs []string
)

// createWorkerCmd represents the createWorker command
var createWorkerCmd = &cobra.Command{
    Use:   "worker <type>",
    Short: "Create a polling job worker",
    Long: `Create a polling job worker which will call the given handler for every job.
The handler will receive the variables of the activated job as JSON object on stdin.
If the handler finishes successful the job will be completed with the variables provided on stdout, again as JSON object.
If the handler exits with an none zero exit code the job will be failed, the handler can provide a failure message on stderr.
`,
    Args:    cobra.ExactArgs(1),
    PreRunE: initClient,
    Run: func(cmd *cobra.Command, args []string) {
        createWorkerHandlerArgs = strings.Split(createWorkerHandlerFlag, " ")

        jobWorker := client.NewJobWorker().
            JobType(args[0]).
            Handler(handle).
            Name(createWorkerNameFlag).
            Timeout(createWorkerTimeoutFlag).
            MaxJobsActive(createWorkerMaxJobsActiveFlag).
            Concurrency(createWorkerConcurrencyFlag).
            PollInterval(createWorkerPollIntervalFlag).
            PollThreshold(createWorkerPollThresholdFlag).
            Open()

        jobWorker.AwaitClose()
    },
}

func handle(jobClient worker.JobClient, job entities.Job) {
    key := job.Key
    variables := job.Variables
    log.Println("Activated job", key, "with variables", variables)

    command := exec.Command(createWorkerHandlerArgs[0], createWorkerHandlerArgs[1:]...)

    // capture stdout and stderr for completing/failing job
    var stdout, stderr bytes.Buffer
    command.Stdout = &stdout
    command.Stderr = &stderr

    // get stdin of handler command and send variables
    stdin, err := command.StdinPipe()
    if err != nil {
        log.Fatal("Failed to get stdin for command", createWorkerHandlerFlag, err)
    }
    io.WriteString(stdin, variables)
    stdin.Close()

    // start and wait for handler command to finish
    err = command.Start()
    if err != nil {
        log.Fatal("Failed to start command", createWorkerHandlerFlag, err)
    }

    if command.Wait() == nil {
        variables := string(stdout.Bytes())
        if len(variables) < 2 {
            // use empty variables if non was returned
            variables = "{}"
        }
        completeJob(jobClient, job, variables)
    } else {
        failJob(jobClient, job, string(stderr.Bytes()))
    }
}

func completeJob(jobClient worker.JobClient, job entities.Job, variables string) {
    key := job.Key
    request, err := jobClient.NewCompleteJobCommand().JobKey(key).VariablesFromString(variables)
    if err != nil {
        failJob(jobClient, job, fmt.Sprint("Unable to set variables", variables, "to complete job", key, err))
    } else {
        log.Println("Handler completed job", job.Key, "with variables", variables)

        _, err = request.Send()
        if err != nil {
            log.Println("Unable to complete job", key, err)
        }
    }
}

func failJob(jobClient worker.JobClient, job entities.Job, error string) {
    log.Println("Command failed to handle job", job.Key, error)
    _, err := jobClient.NewFailJobCommand().JobKey(job.Key).Retries(job.Retries - 1).ErrorMessage(error).Send()
    if err != nil {
        log.Println("Unable to fail job", err)
    }
}

func init() {
    createCmd.AddCommand(createWorkerCmd)

    createWorkerCmd.Flags().StringVar(&createWorkerHandlerFlag, "handler", "", "Specify handler to invoke for each job")
    createWorkerCmd.MarkFlagRequired("handler")

    createWorkerCmd.Flags().StringVar(&createWorkerNameFlag, "name", DefaultJobWorkerName, "Specify the worker name")
    createWorkerCmd.Flags().DurationVar(&createWorkerTimeoutFlag, "timeout", commands.DefaultJobTimeout, "Specify the duration no other worker should work on job activated by this worker")
    createWorkerCmd.Flags().IntVar(&createWorkerMaxJobsActiveFlag, "maxJobsActive", worker.DefaultJobWorkerMaxJobActive, "Specify the maximum number of jobs which will be activated for this worker at the same time")
    createWorkerCmd.Flags().IntVar(&createWorkerConcurrencyFlag, "concurrency", worker.DefaultJobWorkerConcurrency, "Specify the maximum number of concurrent spawned goroutines to complete jobs")
    createWorkerCmd.Flags().DurationVar(&createWorkerPollIntervalFlag, "pollInterval", worker.DefaultJobWorkerPollInterval, "Specify the maximal interval between polling for new jobs")
    createWorkerCmd.Flags().Float64Var(&createWorkerPollThresholdFlag, "pollThreshold", worker.DefaultJobWorkerPollThreshold, "Specify the threshold of buffered activated jobs before polling for new jobs, i.e. pollThreshold * maxJobsActive")
}
