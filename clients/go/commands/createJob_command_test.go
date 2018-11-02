package commands

import (
	"github.com/golang/mock/gomock"
	"github.com/zeebe-io/zeebe/clients/go/mock_pb"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/utils"
	"testing"
)

func TestCreateJobCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.CreateJobRequest{
		JobType:       "foo",
		Retries:       DefaultJobRetries,
		CustomHeaders: "{}",
	}
	stub := &pb.CreateJobResponse{
		Key: 123,
	}

	client.EXPECT().CreateJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateJobCommand(client, utils.DefaultTestTimeout)

	response, err := command.JobType("foo").Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateJobCommandWithRetries(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.CreateJobRequest{
		JobType:       "foo",
		Retries:       23,
		CustomHeaders: "{}",
	}
	stub := &pb.CreateJobResponse{
		Key: 123,
	}

	client.EXPECT().CreateJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateJobCommand(client, utils.DefaultTestTimeout)

	response, err := command.JobType("foo").Retries(23).Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateJobCommandAddingCustomHeaders(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	customHeaders := "{\"foo\":\"bar\",\"hello\":23}"

	request := &pb.CreateJobRequest{
		JobType:       "foo",
		Retries:       DefaultJobRetries,
		CustomHeaders: customHeaders,
	}
	stub := &pb.CreateJobResponse{
		Key: 123,
	}

	client.EXPECT().CreateJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateJobCommand(client, utils.DefaultTestTimeout)

	response, err := command.JobType("foo").AddCustomHeader("foo", "bar").AddCustomHeader("hello", 23).Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateJobCommandWithCustomHeadersFromString(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	customHeaders := "{\"foo\":\"bar\"}"

	request := &pb.CreateJobRequest{
		JobType:       "foo",
		Retries:       DefaultJobRetries,
		CustomHeaders: customHeaders,
	}
	stub := &pb.CreateJobResponse{
		Key: 123,
	}

	client.EXPECT().CreateJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateJobCommand(client, utils.DefaultTestTimeout)

	headersCommand, err := command.JobType("foo").SetCustomHeadersFromString(customHeaders)
	if err != nil {
		t.Error("Failed to set customHeaders: ", err)
	}

	response, err := headersCommand.Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateJobCommandWithCustomHeadersFromStringer(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	customHeaders := "{\"foo\":\"bar\"}"

	request := &pb.CreateJobRequest{
		JobType:       "foo",
		Retries:       DefaultJobRetries,
		CustomHeaders: customHeaders,
	}
	stub := &pb.CreateJobResponse{
		Key: 123,
	}

	client.EXPECT().CreateJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateJobCommand(client, utils.DefaultTestTimeout)

	headersCommand, err := command.JobType("foo").SetCustomHeadersFromStringer(DataType{Foo: "bar"})
	if err != nil {
		t.Error("Failed to set customHeaders: ", err)
	}

	response, err := headersCommand.Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateJobCommandWithCustomHeadersFromObject(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	customHeaders := "{\"foo\":\"bar\"}"

	request := &pb.CreateJobRequest{
		JobType:       "foo",
		Retries:       DefaultJobRetries,
		CustomHeaders: customHeaders,
	}
	stub := &pb.CreateJobResponse{
		Key: 123,
	}

	client.EXPECT().CreateJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateJobCommand(client, utils.DefaultTestTimeout)

	headersCommand, err := command.JobType("foo").SetCustomHeadersFromObject(DataType{Foo: "bar"})
	if err != nil {
		t.Error("Failed to set customHeaders: ", err)
	}

	response, err := headersCommand.Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateJobCommandWithCustomHeadersFromMap(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	customHeaders := "{\"foo\":\"bar\"}"
	customHeadersMap := make(map[string]interface{})
	customHeadersMap["foo"] = "bar"

	request := &pb.CreateJobRequest{
		JobType:       "foo",
		Retries:       DefaultJobRetries,
		CustomHeaders: customHeaders,
	}
	stub := &pb.CreateJobResponse{
		Key: 123,
	}

	client.EXPECT().CreateJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateJobCommand(client, utils.DefaultTestTimeout)

	headersCommand, err := command.JobType("foo").SetCustomHeadersFromMap(customHeadersMap)
	if err != nil {
		t.Error("Failed to set customHeaders: ", err)
	}

	response, err := headersCommand.Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateJobCommandWithPayloadFromString(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"

	request := &pb.CreateJobRequest{
		JobType:       "foo",
		Retries:       DefaultJobRetries,
		CustomHeaders: "{}",
		Payload:       payload,
	}
	stub := &pb.CreateJobResponse{
		Key: 123,
	}

	client.EXPECT().CreateJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateJobCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.JobType("foo").PayloadFromString(payload)
	if err != nil {
		t.Error("Failed to set payload: ", err)
	}

	response, err := payloadCommand.Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateJobCommandWithPayloadFromStringer(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"

	request := &pb.CreateJobRequest{
		JobType:       "foo",
		Retries:       DefaultJobRetries,
		CustomHeaders: "{}",
		Payload:       payload,
	}
	stub := &pb.CreateJobResponse{
		Key: 123,
	}

	client.EXPECT().CreateJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateJobCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.JobType("foo").PayloadFromStringer(DataType{Foo: "bar"})
	if err != nil {
		t.Error("Failed to set payload: ", err)
	}

	response, err := payloadCommand.Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateJobCommandWithPayloadFromObject(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"

	request := &pb.CreateJobRequest{
		JobType:       "foo",
		Retries:       DefaultJobRetries,
		CustomHeaders: "{}",
		Payload:       payload,
	}
	stub := &pb.CreateJobResponse{
		Key: 123,
	}

	client.EXPECT().CreateJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateJobCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.JobType("foo").PayloadFromObject(DataType{Foo: "bar"})
	if err != nil {
		t.Error("Failed to set payload: ", err)
	}

	response, err := payloadCommand.Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestCreateJobCommandWithPayloadFromMap(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	payload := "{\"foo\":\"bar\"}"
	payloadMap := make(map[string]interface{})
	payloadMap["foo"] = "bar"

	request := &pb.CreateJobRequest{
		JobType:       "foo",
		Retries:       DefaultJobRetries,
		CustomHeaders: "{}",
		Payload:       payload,
	}
	stub := &pb.CreateJobResponse{
		Key: 123,
	}

	client.EXPECT().CreateJob(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewCreateJobCommand(client, utils.DefaultTestTimeout)

	payloadCommand, err := command.JobType("foo").PayloadFromMap(payloadMap)
	if err != nil {
		t.Error("Failed to set payload: ", err)
	}

	response, err := payloadCommand.Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}
