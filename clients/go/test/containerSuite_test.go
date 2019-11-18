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
package test

import (
	"context"
	"fmt"
	"github.com/docker/go-connections/nat"
	"github.com/stretchr/testify/suite"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"
	"github.com/zeebe-io/zeebe/clients/go/pkg/zbc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"time"
)

type zeebeWaitStrategy struct {
	timeout time.Duration
}

func (s zeebeWaitStrategy) WaitUntilReady(ctx context.Context, target wait.StrategyTarget) error {
	host, err := target.Host(ctx)
	if err != nil {
		return err
	}

	zeebePort, err := nat.NewPort("", "26500")
	if err != nil {
		return err
	}

	mappedPort, err := target.MappedPort(ctx, zeebePort)
	if err != nil {
		return err
	}

	client, err := zbc.NewZBClientWithConfig(&zbc.ZBClientConfig{
		UsePlaintextConnection: true,
		GatewayAddress:         fmt.Sprintf("%s:%d", host, mappedPort.Int()),
	})
	if err != nil {
		return err
	}

	defer func() {
		err := client.Close()
		if err != nil {
			fmt.Println("Failed to close ZB client: ", err)
		}
	}()

	_, err = client.NewTopologyCommand().Send()

	for err != nil && status.Code(err) == codes.Unavailable {
		time.Sleep(s.timeout)
		_, err = client.NewTopologyCommand().Send()
	}

	return err
}

type containerSuite struct {
	// timeout specifies the wait period before checking if the container is up
	timeout time.Duration
	// containerImage is the ID of docker image to be used
	containerImage string

	suite.Suite
	container testcontainers.Container
	client    zbc.ZBClient
}

func (s *containerSuite) SetupSuite() {
	var err error
	req := testcontainers.GenericContainerRequest{
		ContainerRequest: testcontainers.ContainerRequest{
			Image:        s.containerImage,
			ExposedPorts: []string{"26500"},
			WaitingFor:   zeebeWaitStrategy{timeout: s.timeout},
		},
		Started: true,
	}

	ctx := context.Background()
	s.container, err = testcontainers.GenericContainer(ctx, req)
	if err != nil {
		s.T().Fatal(err)
	}

	host, err := s.container.Host(ctx)
	if err != nil {
		s.T().Fatal(err)
	}

	port, err := s.container.MappedPort(ctx, "26500")
	if err != nil {
		s.T().Fatal(err)
	}

	s.client, err = zbc.NewZBClientWithConfig(&zbc.ZBClientConfig{
		UsePlaintextConnection: true,
		GatewayAddress:         fmt.Sprintf("%s:%d", host, port.Int()),
	})

	if err != nil {
		s.T().Fatal(err)
	}
}

func (s *containerSuite) TearDownSuite() {
	err := s.client.Close()
	if err != nil {
		s.T().Fatal(err)
	}

	err = s.container.Terminate(context.Background())
	if err != nil {
		s.T().Fatal(err)
	}
}
