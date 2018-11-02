package commands

import (
	"context"
	"github.com/zeebe-io/zeebe/clients/go/entities"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"io"
	"time"
)

const (
	DefaultJobTimeout     = time.Duration(5 * time.Minute)
	DefaultJobTimeoutInMs = int64(DefaultJobTimeout / time.Millisecond)
	DefaultJobWorkerName  = "default"
)

type DispatchActivateJobsCommand interface {
	Send() ([]entities.Job, error)
}

type ActivateJobsCommandStep1 interface {
	JobType(string) ActivateJobsCommandStep2
}

type ActivateJobsCommandStep2 interface {
	Amount(int32) ActivateJobsCommandStep3
}

type ActivateJobsCommandStep3 interface {
	DispatchActivateJobsCommand

	Timeout(time.Duration) ActivateJobsCommandStep3
	WorkerName(string) ActivateJobsCommandStep3
}

type ActivateJobsCommand struct {
	request        *pb.ActivateJobsRequest
	gateway        pb.GatewayClient
	requestTimeout time.Duration
}

func (cmd *ActivateJobsCommand) JobType(jobType string) ActivateJobsCommandStep2 {
	cmd.request.Type = jobType
	return cmd
}

func (cmd *ActivateJobsCommand) Amount(amount int32) ActivateJobsCommandStep3 {
	cmd.request.Amount = amount
	return cmd
}

func (cmd *ActivateJobsCommand) Timeout(timeout time.Duration) ActivateJobsCommandStep3 {
	cmd.request.Timeout = int64(timeout / time.Millisecond)
	return cmd
}

func (cmd *ActivateJobsCommand) WorkerName(workerName string) ActivateJobsCommandStep3 {
	cmd.request.Worker = workerName
	return cmd
}

func (cmd *ActivateJobsCommand) Send() ([]entities.Job, error) {
	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	stream, err := cmd.gateway.ActivateJobs(ctx, cmd.request)
	if err != nil {
		return nil, err
	}

	var activatedJobs []entities.Job

	for {
		response, err := stream.Recv()
		if err == io.EOF {
			break
		}
		if err != nil {
			return activatedJobs, err
		}
		for _, activatedJob := range response.Jobs {
			activatedJobs = append(activatedJobs, entities.Job{*activatedJob})
		}
	}

	return activatedJobs, nil
}

func NewActivateJobsCommand(gateway pb.GatewayClient, requestTimeout time.Duration) ActivateJobsCommandStep1 {
	return &ActivateJobsCommand{
		request: &pb.ActivateJobsRequest{
			Timeout: DefaultJobTimeoutInMs,
			Worker:  DefaultJobWorkerName,
		},
		gateway:        gateway,
		requestTimeout: requestTimeout,
	}
}
