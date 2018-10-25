package commands

import (
	"context"
	"fmt"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"time"
)

const (
	DefaultJobRetries = 3
)

type DispatchCreateJobCommand interface {
	Send() (*pb.CreateJobResponse, error)
}

type CreateJobCommandStep1 interface {
	JobType(string) CreateJobCommandStep2
}

type CreateJobCommandStep2 interface {
	DispatchCreateJobCommand

	SetCustomHeadersFromString(string) (CreateJobCommandStep2, error)
	SetCustomHeadersFromStringer(fmt.Stringer) (CreateJobCommandStep2, error)
	SetCustomHeadersFromMap(map[string]interface{}) (CreateJobCommandStep2, error)
	SetCustomHeadersFromObject(interface{}) (CreateJobCommandStep2, error)

	AddCustomHeader(string, interface{}) CreateJobCommandStep2

	Retries(int32) CreateJobCommandStep2

	PayloadFromString(string) (CreateJobCommandStep2, error)
	PayloadFromStringer(fmt.Stringer) (CreateJobCommandStep2, error)
	PayloadFromMap(map[string]interface{}) (CreateJobCommandStep2, error)
	PayloadFromObject(interface{}) (CreateJobCommandStep2, error)
}

type CreateJobCommand struct {
	utils.SerializerMixin

	customHeaders map[string]interface{}

	request        *pb.CreateJobRequest
	gateway        pb.GatewayClient
	requestTimeout time.Duration
}

func (cmd *CreateJobCommand) GetRequest() *pb.CreateJobRequest {
	return cmd.request
}

func (cmd *CreateJobCommand) SetCustomHeadersFromObject(header interface{}) (CreateJobCommandStep2, error) {
	value, err := cmd.AsJson("custom headers", header)
	if err != nil {
		return nil, err
	}

	cmd.request.CustomHeaders = value
	return cmd, nil
}

func (cmd *CreateJobCommand) SetCustomHeadersFromMap(headers map[string]interface{}) (CreateJobCommandStep2, error) {
	return cmd.SetCustomHeadersFromObject(headers)
}

func (cmd *CreateJobCommand) SetCustomHeadersFromString(jsonString string) (CreateJobCommandStep2, error) {
	err := cmd.Validate("custom headers", jsonString)
	if err != nil {
		return nil, err
	}

	cmd.request.CustomHeaders = jsonString
	return cmd, err
}

func (cmd *CreateJobCommand) SetCustomHeadersFromStringer(jsonString fmt.Stringer) (CreateJobCommandStep2, error) {
	return cmd.SetCustomHeadersFromString(jsonString.String())
}

func (cmd *CreateJobCommand) AddCustomHeader(key string, value interface{}) CreateJobCommandStep2 {
	cmd.customHeaders[key] = value
	return cmd
}

func (cmd *CreateJobCommand) Retries(retries int32) CreateJobCommandStep2 {
	cmd.request.Retries = retries
	return cmd
}

func (cmd *CreateJobCommand) PayloadFromString(payload string) (CreateJobCommandStep2, error) {
	err := cmd.Validate("payload", payload)
	if err != nil {
		return nil, err
	}

	cmd.request.Payload = payload
	return cmd, nil
}

func (cmd *CreateJobCommand) PayloadFromStringer(payload fmt.Stringer) (CreateJobCommandStep2, error) {
	return cmd.PayloadFromString(payload.String())
}

func (cmd *CreateJobCommand) PayloadFromObject(payload interface{}) (CreateJobCommandStep2, error) {
	value, err := cmd.AsJson("payload", payload)
	if err != nil {
		return nil, err
	}

	cmd.request.Payload = value
	return cmd, nil
}

func (cmd *CreateJobCommand) PayloadFromMap(payload map[string]interface{}) (CreateJobCommandStep2, error) {
	return cmd.PayloadFromObject(payload)
}

func (cmd *CreateJobCommand) JobType(jobType string) CreateJobCommandStep2 {
	cmd.request.JobType = jobType
	return cmd
}

func (cmd *CreateJobCommand) Send() (*pb.CreateJobResponse, error) {
	if len(cmd.request.CustomHeaders) == 0 {
		if _, err := cmd.SetCustomHeadersFromMap(cmd.customHeaders); err != nil {
			return nil, err
		}
	}

	ctx, cancel := context.WithTimeout(context.Background(), cmd.requestTimeout)
	defer cancel()

	return cmd.gateway.CreateJob(ctx, cmd.request)
}

func NewCreateJobCommand(gateway pb.GatewayClient, requestTimeout time.Duration) CreateJobCommandStep1 {
	return &CreateJobCommand{
		SerializerMixin: utils.NewJsonStringSerializer(),
		customHeaders:   make(map[string]interface{}),
		request: &pb.CreateJobRequest{
			Retries: DefaultJobRetries,
		},
		gateway:        gateway,
		requestTimeout: requestTimeout,
	}
}
