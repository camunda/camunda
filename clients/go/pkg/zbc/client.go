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
	"errors"
	"fmt"
	"google.golang.org/grpc/credentials"
	"google.golang.org/grpc/keepalive"
	"log"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/zeebe-io/zeebe/clients/go/pkg/commands"
	"github.com/zeebe-io/zeebe/clients/go/pkg/pb"
	"github.com/zeebe-io/zeebe/clients/go/pkg/worker"
	"google.golang.org/grpc"
)

const DefaultRequestTimeout = 15 * time.Second
const DefaultKeepAlive = 45 * time.Second
const InsecureEnvVar = "ZEEBE_INSECURE_CONNECTION"
const CaCertificatePath = "ZEEBE_CA_CERTIFICATE_PATH"
const KeepAliveEnvVar = "ZEEBE_KEEP_ALIVE"

type ClientImpl struct {
	gateway             pb.GatewayClient
	requestTimeout      time.Duration
	connection          *grpc.ClientConn
	credentialsProvider CredentialsProvider
}

type ClientConfig struct {
	GatewayAddress         string
	UsePlaintextConnection bool
	CaCertificatePath      string
	CredentialsProvider    CredentialsProvider
	// KeepAlive can be used configure how often keep alive messages should be sent to the gateway. These will be sent
	// whether or not there are active requests. Negative values will result in error and zero will result in the default
	// of 45 seconds being used
	KeepAlive time.Duration
}

// ErrFileNotFound is returned whenever a file can't be found at the provided path. Use this value to do error comparison.
const ErrFileNotFound = Error("file not found")

type Error string

func (e Error) Error() string {
	return string(e)
}

func (client *ClientImpl) NewTopologyCommand() *commands.TopologyCommand {
	return commands.NewTopologyCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ClientImpl) NewDeployWorkflowCommand() *commands.DeployCommand {
	return commands.NewDeployCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ClientImpl) NewPublishMessageCommand() commands.PublishMessageCommandStep1 {
	return commands.NewPublishMessageCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ClientImpl) NewResolveIncidentCommand() commands.ResolveIncidentCommandStep1 {
	return commands.NewResolveIncidentCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ClientImpl) NewCreateInstanceCommand() commands.CreateInstanceCommandStep1 {
	return commands.NewCreateInstanceCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ClientImpl) NewCancelInstanceCommand() commands.CancelInstanceStep1 {
	return commands.NewCancelInstanceCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ClientImpl) NewCompleteJobCommand() commands.CompleteJobCommandStep1 {
	return commands.NewCompleteJobCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ClientImpl) NewFailJobCommand() commands.FailJobCommandStep1 {
	return commands.NewFailJobCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ClientImpl) NewUpdateJobRetriesCommand() commands.UpdateJobRetriesCommandStep1 {
	return commands.NewUpdateJobRetriesCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ClientImpl) NewSetVariablesCommand() commands.SetVariablesCommandStep1 {
	return commands.NewSetVariablesCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ClientImpl) NewActivateJobsCommand() commands.ActivateJobsCommandStep1 {
	return commands.NewActivateJobsCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ClientImpl) NewThrowErrorCommand() commands.ThrowErrorCommandStep1 {
	return commands.NewThrowErrorCommand(client.gateway, client.requestTimeout, client.credentialsProvider.ShouldRetryRequest)
}

func (client *ClientImpl) NewJobWorker() worker.JobWorkerBuilderStep1 {
	return worker.NewJobWorkerBuilder(client.gateway, client, client.requestTimeout)
}

func (client *ClientImpl) SetRequestTimeout(requestTimeout time.Duration) Client {
	client.requestTimeout = requestTimeout
	return client
}

func (client *ClientImpl) Close() error {
	return client.connection.Close()
}

func NewClient(config *ClientConfig) (Client, error) {
	var opts []grpc.DialOption
	err := applyClientEnvOverrides(config)
	if err != nil {
		return nil, err
	}

	opts, err = configureConnectionSecurity(config, opts)
	if err != nil {
		return nil, err
	}

	opts, err = configureCredentialsProvider(config, opts)
	if err != nil {
		return nil, err
	}

	opts, err = configureKeepAlive(config, opts)
	if err != nil {
		return nil, err
	}

	conn, err := grpc.Dial(config.GatewayAddress, opts...)
	if err != nil {
		return nil, err
	}

	return &ClientImpl{
		gateway:             pb.NewGatewayClient(conn),
		requestTimeout:      DefaultRequestTimeout,
		connection:          conn,
		credentialsProvider: config.CredentialsProvider,
	}, nil
}

func applyClientEnvOverrides(config *ClientConfig) error {
	if insecureConn := env.get(InsecureEnvVar); insecureConn != "" {
		config.UsePlaintextConnection = insecureConn == "true"
	}

	if caCertificatePath := env.get(CaCertificatePath); caCertificatePath != "" {
		config.CaCertificatePath = caCertificatePath
	}

	if val := env.get(KeepAliveEnvVar); val != "" {
		keepAlive, err := strconv.ParseUint(val, 10, 64)
		if err != nil {
			return fmt.Errorf("keep alive must be expressed as positive number of milliseconds: %w", err)
		}

		config.KeepAlive = time.Duration(keepAlive) * time.Millisecond
	}

	return nil
}

func configureCredentialsProvider(config *ClientConfig, opts []grpc.DialOption) ([]grpc.DialOption, error) {
	if config.CredentialsProvider == nil && shouldUseDefaultCredentialsProvider() {
		if err := setDefaultCredentialsProvider(config); err != nil {
			return nil, err
		}
	}

	if config.CredentialsProvider != nil {
		if config.UsePlaintextConnection {
			log.Println("Warning: The configured security level does not guarantee that the credentials will be confidential. If this unintentional, please enable transport security.")
		}

		callCredentials := &callCredentials{credentialsProvider: config.CredentialsProvider}
		opts = append(opts, grpc.WithPerRPCCredentials(callCredentials))
	} else {
		config.CredentialsProvider = &NoopCredentialsProvider{}
	}

	return opts, nil
}

func shouldUseDefaultCredentialsProvider() bool {
	return env.get(OAuthClientSecretEnvVar) != "" || env.get(OAuthClientIdEnvVar) != ""
}

func setDefaultCredentialsProvider(config *ClientConfig) error {
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

func configureConnectionSecurity(config *ClientConfig, opts []grpc.DialOption) ([]grpc.DialOption, error) {
	if !config.UsePlaintextConnection {
		var creds credentials.TransportCredentials

		if config.CaCertificatePath == "" {
			creds = credentials.NewTLS(&tls.Config{})
		} else if _, err := os.Stat(config.CaCertificatePath); os.IsNotExist(err) {
			return nil, fmt.Errorf("expected to find CA certificate but no such file at '%s': %w", config.CaCertificatePath, ErrFileNotFound)
		} else {
			creds, err = credentials.NewClientTLSFromFile(config.CaCertificatePath, "")
			if err != nil {
				return nil, err
			}
		}

		opts = append(opts, grpc.WithTransportCredentials(creds))
	} else {
		opts = append(opts, grpc.WithInsecure())
	}

	return opts, nil
}

func configureKeepAlive(config *ClientConfig, opts []grpc.DialOption) ([]grpc.DialOption, error) {
	keepAlive := DefaultKeepAlive

	if config.KeepAlive < time.Duration(0) {
		return nil, errors.New("keep alive must be a positive duration")
	} else if config.KeepAlive != time.Duration(0) {
		keepAlive = config.KeepAlive
	}

	return append(opts, grpc.WithKeepaliveParams(keepalive.ClientParameters{Time: keepAlive})), nil
}
