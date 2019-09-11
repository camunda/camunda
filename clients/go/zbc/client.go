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

package zbc

import (
	"crypto/tls"
	"fmt"
	"github.com/pkg/errors"
	"google.golang.org/grpc/credentials"
	"log"
	"os"
	"strings"
	"time"

	"github.com/zeebe-io/zeebe/clients/go/commands"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"github.com/zeebe-io/zeebe/clients/go/worker"
	"google.golang.org/grpc"
)

const DefaultRequestTimeout = 15 * time.Second

type ZBClientImpl struct {
	gateway             pb.GatewayClient
	requestTimeout      time.Duration
	connection          *grpc.ClientConn
	credentialsProvider CredentialsProvider
}

type ZBClientConfig struct {
	GatewayAddress         string
	UsePlaintextConnection bool
	CaCertificatePath      string
	CredentialsProvider    CredentialsProvider
}

// FileNotFoundError is returned whenever a file can't be found at the provided path. Use this value to do error comparison.
const FileNotFoundError = ZBError("file not found")

// InvalidArgumentError is returned whenever an argument differs from the expected format. Use this value to do error comparison.
const InvalidArgumentError = ZBError("invalid argument")

type ZBError string

func (e ZBError) Error() string {
	return string(e)
}

func (client *ZBClientImpl) NewTopologyCommand() *commands.TopologyCommand {
	return commands.NewTopologyCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ZBClientImpl) NewDeployWorkflowCommand() *commands.DeployCommand {
	return commands.NewDeployCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ZBClientImpl) NewPublishMessageCommand() commands.PublishMessageCommandStep1 {
	return commands.NewPublishMessageCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ZBClientImpl) NewResolveIncidentCommand() commands.ResolveIncidentCommandStep1 {
	return commands.NewResolveIncidentCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ZBClientImpl) NewCreateInstanceCommand() commands.CreateInstanceCommandStep1 {
	return commands.NewCreateInstanceCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ZBClientImpl) NewCancelInstanceCommand() commands.CancelInstanceStep1 {
	return commands.NewCancelInstanceCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ZBClientImpl) NewCompleteJobCommand() commands.CompleteJobCommandStep1 {
	return commands.NewCompleteJobCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ZBClientImpl) NewFailJobCommand() commands.FailJobCommandStep1 {
	return commands.NewFailJobCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ZBClientImpl) NewUpdateJobRetriesCommand() commands.UpdateJobRetriesCommandStep1 {
	return commands.NewUpdateJobRetriesCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ZBClientImpl) NewSetVariablesCommand() commands.SetVariablesCommandStep1 {
	return commands.NewSetVariablesCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ZBClientImpl) NewActivateJobsCommand() commands.ActivateJobsCommandStep1 {
	return commands.NewActivateJobsCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ZBClientImpl) NewJobWorker() worker.JobWorkerBuilderStep1 {
	return worker.NewJobWorkerBuilder(client.gateway, client, client.requestTimeout)
}

func (client *ZBClientImpl) SetRequestTimeout(requestTimeout time.Duration) ZBClient {
	client.requestTimeout = requestTimeout
	return client
}

func (client *ZBClientImpl) Close() error {
	return client.connection.Close()
}

func NewZBClient(gatewayAddress string) (ZBClient, error) {
	return NewZBClientWithConfig(&ZBClientConfig{GatewayAddress: gatewayAddress})
}

func NewZBClientWithConfig(config *ZBClientConfig) (ZBClient, error) {
	var opts []grpc.DialOption

	if err := configureConnectionSecurity(config, &opts); err != nil {
		return nil, err
	}

	if err := configureCredentialsProvider(config, &opts); err != nil {
		return nil, err
	}

	conn, err := grpc.Dial(config.GatewayAddress, opts...)
	if err != nil {
		return nil, err
	}

	return &ZBClientImpl{
		gateway:             pb.NewGatewayClient(conn),
		requestTimeout:      DefaultRequestTimeout,
		connection:          conn,
		credentialsProvider: config.CredentialsProvider,
	}, nil
}

func configureCredentialsProvider(config *ZBClientConfig, opts *[]grpc.DialOption) error {
	if config.CredentialsProvider == nil && shouldUseDefaultCredentialsProvider() {
		if err := setDefaultCredentialsProvider(config); err != nil {
			return err
		}
	}

	if config.CredentialsProvider != nil {
		if config.UsePlaintextConnection {
			log.Println("Warning: The configured security level does not guarantee that the credentials will be confidential. If this unintentional, please enable transport security.")
		}

		callCredentials := &zeebeCallCredentials{credentialsProvider: config.CredentialsProvider}
		*opts = append(*opts, grpc.WithPerRPCCredentials(callCredentials))
	} else {
		config.CredentialsProvider = &NoopCredentialsProvider{}
	}

	return nil
}

func shouldUseDefaultCredentialsProvider() bool {
	return os.Getenv(OAuthClientSecretEnvVar) != "" || os.Getenv(OAuthClientIdEnvVar) != ""
}

func setDefaultCredentialsProvider(config *ZBClientConfig) error {
	var audience string
	index := strings.LastIndex(config.GatewayAddress, ":")
	if index > 0 {
		audience = config.GatewayAddress[0:index]
	}

	provider, err := NewOAuthCredentialsProvider(&OAuthProviderConfig{Audience: audience})
	if err != nil {
		return err
	}

	config.CredentialsProvider = provider
	return nil
}

func configureConnectionSecurity(config *ZBClientConfig, opts *[]grpc.DialOption) error {
	if !config.UsePlaintextConnection {
		var creds credentials.TransportCredentials

		if config.CaCertificatePath == "" {
			creds = credentials.NewTLS(&tls.Config{})
		} else if _, err := os.Stat(config.CaCertificatePath); os.IsNotExist(err) {
			return fileNotFoundError("CA certificate", config.CaCertificatePath)
		} else {
			creds, err = credentials.NewClientTLSFromFile(config.CaCertificatePath, "")
			if err != nil {
				return err
			}
		}

		*opts = append(*opts, grpc.WithTransportCredentials(creds))
	} else {
		*opts = append(*opts, grpc.WithInsecure())
	}

	return nil
}

func fileNotFoundError(fileDescription, path string) error {
	return errors.Wrap(FileNotFoundError, fmt.Sprintf("expected to find %s but there was no such file at path '%s'", fileDescription, path))
}

func invalidArgumentError(argument, errorReason string) error {
	return errors.Wrap(InvalidArgumentError, fmt.Sprintf("expected to find valid %s but found error: %s", argument, errorReason))
}
