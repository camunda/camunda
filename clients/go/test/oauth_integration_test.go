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
	"bytes"
	"context"
	"encoding/json"
	"github.com/camunda/zeebe/clients/go/v8/pkg/zbc"
	"github.com/docker/go-connections/nat"
	"github.com/stretchr/testify/suite"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"
	"net/http"
	"os"
	"strings"
	"testing"
	"time"
)

var publicPort nat.Port
var adminPort nat.Port

func init() {
	publicPort, _ = nat.NewPort("tcp", "4444")
	adminPort, _ = nat.NewPort("tcp", "4445")
}

type oauthIntegrationTestSuite struct {
	suite.Suite
	container testcontainers.Container
}

func TestOAuthIntegration(t *testing.T) {
	suite.Run(t, &oauthIntegrationTestSuite{})
}

func (s *oauthIntegrationTestSuite) SetupSuite() {
	req := testcontainers.GenericContainerRequest{
		ContainerRequest: testcontainers.ContainerRequest{
			Image:        "oryd/hydra:v1.11",
			ExposedPorts: []string{"4444", "4445"},
			Env:          map[string]string{"DSN": "memory"},
			WaitingFor:   wait.ForAll(wait.ForListeningPort(publicPort), wait.ForListeningPort(adminPort)),
			Cmd:          []string{"serve", "all", "--dangerous-force-http"},
		},
		Started: true,
	}

	container, err := testcontainers.GenericContainer(context.Background(), req)
	s.Require().NoError(err)

	s.container = container
	s.ensureOAuthClientExists()
}

func (s *oauthIntegrationTestSuite) TearDownSuite() {
	if s.container != nil {
		_ = s.container.Terminate(context.Background())
	}
}
func (s *oauthIntegrationTestSuite) TestFetchOAuthToken() {
	// given
	headers := make(map[string]string, 1)
	endpoint, err := s.getEndpoint(publicPort, "oauth2/token")
	s.Require().NoError(err)

	file, err := os.CreateTemp("/tmp", "oauthCredsCache")
	s.Require().NoError(err)
	defer os.Remove(file.Name())
	cache, err := zbc.NewOAuthYamlCredentialsCache(file.Name())
	s.Require().NoError(err)

	credsProvider, err := zbc.NewOAuthCredentialsProvider(&zbc.OAuthProviderConfig{
		ClientID:               "zeebe",
		ClientSecret:           "secret",
		Audience:               "zeebe",
		AuthorizationServerURL: endpoint,
		Cache:                  cache,
	})
	s.Require().NoError(err)

	// when
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	err = credsProvider.ApplyCredentials(ctx, headers)
	s.Require().NoError(err)

	// then
	s.validateToken(headers["Authorization"])
}

func (s *oauthIntegrationTestSuite) validateToken(authHeaderValue string) {
	httpClient := &http.Client{}
	endpoint, err := s.getEndpoint(adminPort, "oauth2/introspect")
	s.Require().NoError(err)

	body := []byte(`token=` + strings.TrimPrefix(authHeaderValue, "Bearer "))
	request, err := http.NewRequest("POST", endpoint, bytes.NewBuffer(body))
	s.Require().NoError(err)

	request.Header.Add("Content-Type", "application/x-www-form-urlencoded")
	request.Header.Add("Accept", "application/json")

	response, err := httpClient.Do(request)
	s.Require().NoError(err)
	defer response.Body.Close()
	s.Require().Equal(response.StatusCode, 200)

	payload := make(map[string]interface{}, 1)
	err = json.NewDecoder(response.Body).Decode(&payload)
	s.Require().NoError(err)
	s.Require().Equal(payload["active"], true)
}

func (s *oauthIntegrationTestSuite) ensureOAuthClientExists() {
	endpoint, err := s.getEndpoint(adminPort, "clients")
	s.Require().NoError(err)

	body := []byte(`{
        "client_id": "zeebe", "client_secret": "secret", "client_name": "zeebe",
        "grant_types": ["client_credentials"], "audience": ["zeebe"], "response_types": ["code"],
        "token_endpoint_auth_method": "client_secret_post"
    }`)
	response, err := http.Post(endpoint, "application/json", bytes.NewBuffer(body))
	s.Require().NoError(err)
	s.Require().Equal(response.StatusCode, 201)
}

func (s *oauthIntegrationTestSuite) getEndpoint(port nat.Port, path string) (string, error) {
	endpoint, err := s.container.PortEndpoint(context.Background(), port, "http")
	if err != nil {
		return "", err
	}

	return endpoint + "/" + path, nil
}
