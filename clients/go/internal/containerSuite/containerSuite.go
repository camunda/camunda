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
package containerSuite

import (
	"context"
	"fmt"
	"github.com/docker/docker/client"
	"github.com/docker/go-connections/nat"
	"github.com/stretchr/testify/suite"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"
	"github.com/zeebe-io/zeebe/clients/go/pkg/pb"
	"github.com/zeebe-io/zeebe/clients/go/pkg/zbc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"time"
)

type zeebeWaitStrategy struct {
	waitTime time.Duration
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

	zbClient, err := zbc.NewClient(&zbc.ClientConfig{
		UsePlaintextConnection: true,
		GatewayAddress:         fmt.Sprintf("%s:%d", host, mappedPort.Int()),
	})
	if err != nil {
		return err
	}

	res, err := zbClient.NewTopologyCommand().Send()
	for (err != nil && status.Code(err) == codes.Unavailable) || !isStable(res) {
		time.Sleep(s.waitTime)
		res, err = zbClient.NewTopologyCommand().Send()
	}

	if err != nil {
		return err
	}

	return zbClient.Close()
}

func isStable(res *pb.TopologyResponse) bool {
	for _, broker := range res.GetBrokers() {
		for _, partition := range broker.Partitions {
			if partition.GetRole() == pb.Partition_LEADER {
				return true
			}
		}
	}

	return false
}

// ContainerSuite sets up a container running Zeebe and tears it down afterwards.
type ContainerSuite struct {
	// WaitTime specifies the wait period before checking if the container is up
	WaitTime time.Duration
	// ContainerImage is the ID of docker image to be used
	ContainerImage string

	// GatewayAddress is the contact point of the spawned Zeebe container specified in the format 'host:port'
	GatewayAddress string

	suite.Suite
	container testcontainers.Container
}

func (s *ContainerSuite) SetupSuite() {
	var err error
	req := testcontainers.GenericContainerRequest{
		ContainerRequest: testcontainers.ContainerRequest{
			Image:        s.ContainerImage,
			ExposedPorts: []string{"26500"},
			WaitingFor:   zeebeWaitStrategy{waitTime: s.WaitTime},
		},
		Started: true,
	}

	ctx := context.Background()
	err = validateImageExists(ctx, s.ContainerImage)
	if err != nil {
		s.T().Fatal(err)
	}

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

	s.GatewayAddress = fmt.Sprintf("%s:%d", host, port.Int())
}

func (s *ContainerSuite) TearDownSuite() {
	err := s.container.Terminate(context.Background())
	if err != nil {
		s.T().Fatal(err)
	}
}

func validateImageExists(ctx context.Context, image string) error {
	dockerClient, err := client.NewClientWithOpts(client.FromEnv)
	if err != nil {
		return err
	}

	_, _, err = dockerClient.ImageInspectWithRaw(ctx, image)
	if err != nil {
		if client.IsErrNotFound(err) {
			return fmt.Errorf("a Docker image containing Zeebe must be built and named '%s'\n", image)
		}

		return err
	}
	return nil
}
