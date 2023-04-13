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
	"fmt"
	"github.com/camunda/zeebe/clients/go/v8/pkg/entities"
	"github.com/camunda/zeebe/clients/go/v8/pkg/worker"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	"golang.org/x/net/context"
	"golang.org/x/oauth2"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"regexp"
	"strings"
	"sync"
	"testing"
	"time"
)

type mutableToken struct {
	value string
}

type oauthCredsProviderTestSuite struct {
	*envSuite
}

func TestOAuthCredsProviderSuite(t *testing.T) {
	suite.Run(t, &oauthCredsProviderTestSuite{envSuite: new(envSuite)})
}

func (s *oauthCredsProviderTestSuite) TestOAuthCredentialsProvider() {
	// given
	truncateDefaultOAuthYamlCacheFile()
	interceptor := newInterceptor(nil)
	gatewayLis, grpcServer := createServerWithUnaryInterceptor(interceptor.interceptUnary)

	go grpcServer.Serve(gatewayLis)
	defer func() {
		grpcServer.Stop()
		_ = gatewayLis.Close()
	}()

	authzServer := mockAuthorizationServer(s.T(), &mutableToken{value: accessToken})
	defer authzServer.Close()

	credsProvider, err := NewOAuthCredentialsProvider(&OAuthProviderConfig{
		ClientID:               clientID,
		ClientSecret:           clientSecret,
		Audience:               audience,
		AuthorizationServerURL: authzServer.URL,
	})

	s.NoError(err)
	parts := strings.Split(gatewayLis.Addr().String(), ":")
	client, err := NewClient(&ClientConfig{
		GatewayAddress:         fmt.Sprintf("0.0.0.0:%s", parts[len(parts)-1]),
		UsePlaintextConnection: true,
		CredentialsProvider:    credsProvider,
	})
	s.NoError(err)

	// when
	_, err = client.NewTopologyCommand().Send(context.Background())

	// then
	s.Error(err)
	if errorStatus, ok := status.FromError(err); ok {
		s.Equal(codes.Unimplemented, errorStatus.Code())
	}
	s.Equal("Bearer "+accessToken, interceptor.authHeader)
}
func (s *oauthCredsProviderTestSuite) TestNoConfigSecureClient() {
	// given
	truncateDefaultOAuthYamlCacheFile()
	interceptor := newInterceptor(nil)
	gatewayLis, grpcServer := createServerWithUnaryInterceptor(interceptor.interceptUnary)

	go grpcServer.Serve(gatewayLis)
	defer func() {
		grpcServer.Stop()
		_ = gatewayLis.Close()
	}()

	authzServer := mockAuthorizationServer(s.T(), &mutableToken{value: accessToken})
	defer authzServer.Close()

	parts := strings.Split(gatewayLis.Addr().String(), ":")

	env.set(GatewayAddressEnvVar, fmt.Sprintf("0.0.0.0:%s", parts[len(parts)-1]))
	env.set(InsecureEnvVar, "true")
	env.set(OAuthClientIdEnvVar, clientID)
	env.set(OAuthClientSecretEnvVar, clientSecret)
	env.set(OAuthTokenAudienceEnvVar, audience)
	env.set(OAuthAuthorizationUrlEnvVar, authzServer.URL)

	// when
	client, err := NewClient(&ClientConfig{})
	s.NoError(err)

	_, err = client.NewTopologyCommand().Send(context.Background())

	// then
	s.Error(err)
	if errorStatus, ok := status.FromError(err); ok {
		s.Equal(codes.Unimplemented, errorStatus.Code())
	}
	s.Equal("Bearer "+accessToken, interceptor.authHeader)
}

func (s *oauthCredsProviderTestSuite) TestOAuthProviderRetry() {
	// given
	truncateDefaultOAuthYamlCacheFile()
	token := &mutableToken{value: "firstToken"}
	first := true
	interceptor := newInterceptor(func(ctx context.Context) (bool, error) {
		if first {
			first = false
			token.value = accessToken
			return false, status.Error(codes.Unauthenticated, "UNAUTHENTICATED")
		}

		return true, nil
	})

	gatewayLis, grpcServer := createServerWithUnaryInterceptor(interceptor.interceptUnary)

	go grpcServer.Serve(gatewayLis)
	defer func() {
		grpcServer.Stop()
		_ = gatewayLis.Close()
	}()

	authzServer := mockAuthorizationServer(s.T(), token)
	defer authzServer.Close()

	credsProvider, err := NewOAuthCredentialsProvider(&OAuthProviderConfig{
		ClientID:               clientID,
		ClientSecret:           clientSecret,
		Audience:               audience,
		AuthorizationServerURL: authzServer.URL,
	})

	s.NoError(err)
	parts := strings.Split(gatewayLis.Addr().String(), ":")
	client, err := NewClient(&ClientConfig{
		GatewayAddress:         fmt.Sprintf("0.0.0.0:%s", parts[len(parts)-1]),
		UsePlaintextConnection: true,
		CredentialsProvider:    credsProvider,
	})
	s.NoError(err)

	// when
	_, err = client.NewTopologyCommand().Send(context.Background())

	// then
	s.Error(err)
	if errorStatus, ok := status.FromError(err); ok {
		s.Equal(codes.Unimplemented, errorStatus.Code())
	}
	s.EqualValues(2, interceptor.interceptCounter)
}

func (s *oauthCredsProviderTestSuite) TestNotRetryWithSameCredentials() {
	// given
	truncateDefaultOAuthYamlCacheFile()
	token := &mutableToken{value: accessToken}

	interceptor := newInterceptor(func(ctx context.Context) (bool, error) {
		return false, status.Error(codes.Unauthenticated, "UNAUTHENTICATED")
	})

	gatewayLis, grpcServer := createServerWithUnaryInterceptor(interceptor.interceptUnary)

	go grpcServer.Serve(gatewayLis)
	defer func() {
		grpcServer.Stop()
		_ = gatewayLis.Close()
	}()

	authzServer := mockAuthorizationServer(s.T(), token)
	defer authzServer.Close()

	credsProvider, err := NewOAuthCredentialsProvider(&OAuthProviderConfig{
		ClientID:               clientID,
		ClientSecret:           clientSecret,
		Audience:               audience,
		AuthorizationServerURL: authzServer.URL,
	})

	s.NoError(err)
	parts := strings.Split(gatewayLis.Addr().String(), ":")
	client, err := NewClient(&ClientConfig{
		GatewayAddress:         fmt.Sprintf("0.0.0.0:%s", parts[len(parts)-1]),
		UsePlaintextConnection: true,
		CredentialsProvider:    credsProvider,
	})
	s.NoError(err)

	// when
	_, err = client.NewTopologyCommand().Send(context.Background())

	// then
	s.Error(err)
	if errorStatus, ok := status.FromError(err); ok {
		s.Equal(codes.Unauthenticated, errorStatus.Code(), fmt.Sprintf("unexpected '%s'", err.Error()))
	}
	s.EqualValues(1, interceptor.interceptCounter)
}

var configErrorTests = []struct {
	name   string
	config *OAuthProviderConfig
}{
	{
		"malformed authorization server URL",
		&OAuthProviderConfig{
			ClientID:               clientID,
			ClientSecret:           clientSecret,
			Audience:               audience,
			AuthorizationServerURL: "foo",
		},
	},
	{
		"missing client id",
		&OAuthProviderConfig{

			ClientSecret:           clientSecret,
			Audience:               audience,
			AuthorizationServerURL: "http://foo",
		},
	},
	{
		"missing client secret",
		&OAuthProviderConfig{
			ClientID:               clientID,
			Audience:               audience,
			AuthorizationServerURL: "http://foo",
		},
	},
	{
		"missing audience",
		&OAuthProviderConfig{
			ClientID:               clientID,
			ClientSecret:           clientSecret,
			AuthorizationServerURL: "http://foo",
		},
	},
}

func TestInvalidOAuthProviderConfigurations(t *testing.T) {
	for _, test := range configErrorTests {
		t.Run(test.name, func(t *testing.T) {
			// given
			truncateDefaultOAuthYamlCacheFile()

			// when
			_, err := NewOAuthCredentialsProvider(test.config)

			// then
			require.Error(t, err)
		})
	}
}

func (s *oauthCredsProviderTestSuite) TestClientIdEnvOverride() {
	// given
	truncateDefaultOAuthYamlCacheFile()
	env.set(OAuthClientIdEnvVar, "envClient")

	config := &OAuthProviderConfig{
		ClientID:               clientID,
		ClientSecret:           clientSecret,
		Audience:               audience,
		AuthorizationServerURL: "http://foo",
	}

	// when
	_, _ = NewOAuthCredentialsProvider(config)

	// then
	s.EqualValues("envClient", config.ClientID)
}

func (s *oauthCredsProviderTestSuite) TestClientSecretEnvOverride() {
	// given
	truncateDefaultOAuthYamlCacheFile()
	env.set(OAuthClientSecretEnvVar, "envSecret")

	config := &OAuthProviderConfig{
		ClientID:               clientID,
		ClientSecret:           clientSecret,
		Audience:               audience,
		AuthorizationServerURL: "http://foo",
	}

	// when
	_, _ = NewOAuthCredentialsProvider(config)

	// then
	s.EqualValues("envSecret", config.ClientSecret)
}

func (s *oauthCredsProviderTestSuite) TestAudienceEnvOverride() {
	// given
	truncateDefaultOAuthYamlCacheFile()
	env.set(OAuthTokenAudienceEnvVar, "envAudience")

	config := &OAuthProviderConfig{
		ClientID:               clientID,
		ClientSecret:           clientSecret,
		Audience:               audience,
		AuthorizationServerURL: "http://foo",
	}

	// when
	_, _ = NewOAuthCredentialsProvider(config)

	// then
	s.EqualValues("envAudience", config.Audience)
}

func (s *oauthCredsProviderTestSuite) TestAuthzUrlEnvOverride() {
	// given
	truncateDefaultOAuthYamlCacheFile()
	env.set(OAuthAuthorizationUrlEnvVar, "https://envAuthzUrl")

	config := &OAuthProviderConfig{
		ClientID:               clientID,
		ClientSecret:           clientSecret,
		Audience:               audience,
		AuthorizationServerURL: "http://foo",
	}

	// when
	_, _ = NewOAuthCredentialsProvider(config)

	// then
	s.EqualValues("https://envAuthzUrl", config.AuthorizationServerURL)
}

func (s *oauthCredsProviderTestSuite) TestOAuthCredentialsProviderCachesCredentials() {
	// create fake gRPC server which returns UNAUTHENTICATED always except if we use the token `accessToken`
	truncateDefaultOAuthYamlCacheFile()
	gatewayLis, grpcServer := createAuthenticatedGrpcServer(accessToken)
	go grpcServer.Serve(gatewayLis)
	defer grpcServer.Stop()

	// setup authorization server to return valid token
	token := mutableToken{accessToken}
	authzServer := mockAuthorizationServer(s.T(), &token)
	defer authzServer.Close()

	// use a fake authorization server which would fail if we actually used it
	credsProvider, err := NewOAuthCredentialsProvider(&OAuthProviderConfig{
		ClientID:               clientID,
		ClientSecret:           clientSecret,
		Audience:               audience,
		AuthorizationServerURL: authzServer.URL,
	})

	s.NoError(err)
	parts := strings.Split(gatewayLis.Addr().String(), ":")
	client, err := NewClient(&ClientConfig{
		GatewayAddress:         fmt.Sprintf("0.0.0.0:%s", parts[len(parts)-1]),
		UsePlaintextConnection: true,
		CredentialsProvider:    credsProvider,
	})
	s.NoError(err)

	// when
	_, err = client.NewTopologyCommand().Send(context.Background())

	// then
	s.NoError(err)
	if errorStatus, ok := status.FromError(err); ok {
		s.Equal(codes.OK, errorStatus.Code())
	}
	cache, err := NewOAuthYamlCredentialsCache("")
	s.NoError(err)
	s.NoError(cache.Refresh())
	s.Equal(accessToken, cache.Get(audience).AccessToken)
}

func (s *oauthCredsProviderTestSuite) TestOAuthCredentialsProviderUsesCachedCredentials() {
	// create fake gRPC server which returns UNAUTHENTICATED always except if we use the token `accessToken`
	gatewayLis, grpcServer := createAuthenticatedGrpcServer(accessToken)
	go grpcServer.Serve(gatewayLis)
	defer grpcServer.Stop()

	authServerCalled := false
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		authServerCalled = true
	}))
	defer ts.Close()

	// setup cache with correct token
	truncateDefaultOAuthYamlCacheFile()
	cache, err := NewOAuthYamlCredentialsCache(DefaultOauthYamlCachePath)
	s.NoError(err)
	err = cache.Update(audience, &oauth2.Token{
		AccessToken: accessToken,
		Expiry:      time.Now().Add(time.Second * 3600),
		TokenType:   "Bearer",
	})
	s.NoError(err)

	// use a fake authorization server which would fail if we actually used it
	credsProvider, err := NewOAuthCredentialsProvider(&OAuthProviderConfig{
		ClientID:               clientID,
		ClientSecret:           clientSecret,
		Audience:               audience,
		AuthorizationServerURL: ts.URL,
	})

	s.NoError(err)
	parts := strings.Split(gatewayLis.Addr().String(), ":")
	client, err := NewClient(&ClientConfig{
		GatewayAddress:         fmt.Sprintf("0.0.0.0:%s", parts[len(parts)-1]),
		UsePlaintextConnection: true,
		CredentialsProvider:    credsProvider,
	})
	s.NoError(err)

	// when
	_, err = client.NewTopologyCommand().Send(context.Background())

	// then
	s.NoError(err)
	if errorStatus, ok := status.FromError(err); ok {
		s.Equal(codes.OK, errorStatus.Code())
	}

	s.False(authServerCalled)

	// when we do it again
	_, err = client.NewTopologyCommand().Send(context.Background())

	// then
	s.NoError(err)
	if errorStatus, ok := status.FromError(err); ok {
		s.Equal(codes.OK, errorStatus.Code())
	}

	s.False(authServerCalled)
}

func (s *oauthCredsProviderTestSuite) TestOAuthCredentialsProviderUpdatesExpiredToken() {
	// create fake gRPC server which returns UNAUTHENTICATED always except if we use the token `accessToken`
	gatewayLis, grpcServer := createAuthenticatedGrpcServer(accessToken)
	go grpcServer.Serve(gatewayLis)
	defer grpcServer.Stop()

	authServerCalled := false
	// setup authorization server to return valid token
	token := mutableToken{accessToken}
	authzServer := mockAuthorizationServer(s.T(), &token)
	defer authzServer.Close()

	ts := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		writer.Header().Set("Content-Type", "application/json")
		responsePayload := []byte("{\"access_token\": \"" + token.value + "\"," +
			"\"expires_in\": 3600," +
			"\"token_type\": \"bearer\"}")

		_, err := writer.Write(responsePayload)
		if err != nil {
			panic(err)
		}
		authServerCalled = true
	}))
	defer ts.Close()

	// setup cache with expired token
	truncateDefaultOAuthYamlCacheFile()
	cache, err := NewOAuthYamlCredentialsCache(DefaultOauthYamlCachePath)
	s.NoError(err)
	err = cache.Update(audience, &oauth2.Token{
		AccessToken: accessToken,
		Expiry:      time.Now().Add(time.Second * -1),
		TokenType:   "Bearer",
	})
	s.NoError(err)

	// use the mock authorization server
	credsProvider, err := NewOAuthCredentialsProvider(&OAuthProviderConfig{
		ClientID:               clientID,
		ClientSecret:           clientSecret,
		Audience:               audience,
		AuthorizationServerURL: ts.URL,
	})

	s.NoError(err)
	parts := strings.Split(gatewayLis.Addr().String(), ":")
	client, err := NewClient(&ClientConfig{
		GatewayAddress:         fmt.Sprintf("0.0.0.0:%s", parts[len(parts)-1]),
		UsePlaintextConnection: true,
		CredentialsProvider:    credsProvider,
	})
	s.NoError(err)

	// when
	_, err = client.NewTopologyCommand().Send(context.Background())

	// then
	s.NoError(err)
	if errorStatus, ok := status.FromError(err); ok {
		s.Equal(codes.OK, errorStatus.Code())
	}

	s.True(authServerCalled)
}

func (s *oauthCredsProviderTestSuite) TestOAuthTimeout() {
	// given
	truncateDefaultOAuthYamlCacheFile()
	block := make(chan struct{})
	lis, grpcServer := createAuthenticatedGrpcServer(accessToken)
	go grpcServer.Serve(lis)
	defer grpcServer.Stop()

	ts := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, req *http.Request) {
		<-block
	}))

	defer func() {
		close(block)
		ts.Close()
	}()

	credsProvider, err := NewOAuthCredentialsProvider(&OAuthProviderConfig{
		ClientID:               clientID,
		ClientSecret:           clientSecret,
		Audience:               audience,
		AuthorizationServerURL: ts.URL,
	})
	s.NoError(err)
	credsProvider.timeout = time.Millisecond

	parts := strings.Split(lis.Addr().String(), ":")
	client, err := NewClient(&ClientConfig{
		GatewayAddress:         fmt.Sprintf("0.0.0.0:%s", parts[len(parts)-1]),
		UsePlaintextConnection: true,
		CredentialsProvider:    credsProvider,
	})
	s.NoError(err)

	// when
	ctx, cancel := context.WithTimeout(context.Background(), time.Hour)
	defer cancel()

	finishCmd := make(chan struct{})
	go func() {
		_, err = client.NewTopologyCommand().Send(ctx)
		finishCmd <- struct{}{}
	}()

	select {
	case <-finishCmd:
	case <-time.After(5 * time.Second):
		s.T().Fatal("expected command to fail, timed out out after 5s")
	}

	// then
	if err == nil {
		s.T().Fatal("expected command to fail")
	}

	s.EqualValues(codes.Canceled, status.Code(err))
}
func (s *oauthCredsProviderTestSuite) TestNoRequestIfOAuthFails() {
	// given
	truncateDefaultOAuthYamlCacheFile()
	interceptor := newInterceptor(func(ctx context.Context) (bool, error) {
		return false, nil
	})

	lis, grpcServer := createServerWithUnaryInterceptor(interceptor.interceptUnary)
	go grpcServer.Serve(lis)
	defer grpcServer.Stop()

	authServer := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, req *http.Request) {
		writer.WriteHeader(400)
	}))

	defer authServer.Close()

	credsProvider, err := NewOAuthCredentialsProvider(&OAuthProviderConfig{
		ClientID:               clientID,
		ClientSecret:           clientSecret,
		Audience:               audience,
		AuthorizationServerURL: authServer.URL,
	})
	s.NoError(err)

	parts := strings.Split(lis.Addr().String(), ":")
	client, err := NewClient(&ClientConfig{
		GatewayAddress:         fmt.Sprintf("0.0.0.0:%s", parts[len(parts)-1]),
		UsePlaintextConnection: true,
		CredentialsProvider:    credsProvider,
	})
	s.NoError(err)

	// when
	ctx, cancel := context.WithTimeout(context.Background(), time.Hour)
	defer cancel()

	finishCmd := make(chan struct{})
	go func() {
		_, err = client.NewTopologyCommand().Send(ctx)
		finishCmd <- struct{}{}
	}()

	select {
	case <-finishCmd:
	case <-time.After(5 * time.Second):
		s.T().Fatal("expected command to fail, timed out out after 5s")
	}

	// then
	if err == nil {
		s.T().Fatal("expected command to fail")
	}

	s.EqualValues(codes.Canceled, status.Code(err))
	s.EqualValues(0, interceptor.interceptCounter)
}

func (s *oauthCredsProviderTestSuite) TestJobPollerRetry() {
	// given
	truncateDefaultOAuthYamlCacheFile()
	token := &mutableToken{value: "firstToken"}
	first := true
	interceptor := newInterceptor(func(ctx context.Context) (bool, error) {
		if first {
			first = false
			token.value = accessToken
			return false, status.Error(codes.Unauthenticated, "UNAUTHENTICATED")
		}

		return true, nil
	})

	gatewayLis, grpcServer := createServerWithStreamInterceptor(interceptor.interceptStream)

	go grpcServer.Serve(gatewayLis)
	defer func() {
		grpcServer.Stop()
		_ = gatewayLis.Close()
	}()

	authzServer := mockAuthorizationServer(s.T(), token)
	defer authzServer.Close()

	var credsProvider CredentialsProvider
	credsProvider, err := NewOAuthCredentialsProvider(&OAuthProviderConfig{
		ClientID:               clientID,
		ClientSecret:           clientSecret,
		Audience:               audience,
		AuthorizationServerURL: authzServer.URL,
	})
	s.NoError(err)
	credsProvider = &custom{provider: credsProvider}

	parts := strings.Split(gatewayLis.Addr().String(), ":")
	client, err := NewClient(&ClientConfig{
		GatewayAddress:         fmt.Sprintf("0.0.0.0:%s", parts[len(parts)-1]),
		UsePlaintextConnection: true,
		CredentialsProvider:    credsProvider,
	})
	s.NoError(err)

	// when
	var wg sync.WaitGroup
	var done bool
	wg.Add(1)

	_ = client.NewJobWorker().JobType("test").Handler(func(client worker.JobClient, job entities.Job) {
		if !done {
			done = true
			wg.Done()
		}
	}).Open()
	wg.Wait()

	// then
	s.GreaterOrEqual(1, credsProvider.(*custom).shouldRetryCalls)
}

func (s *oauthCredsProviderTestSuite) TestJobActivateRetry() {
	// given
	truncateDefaultOAuthYamlCacheFile()
	token := &mutableToken{value: "firstToken"}
	first := true
	interceptor := newInterceptor(func(ctx context.Context) (bool, error) {
		if first {
			first = false
			token.value = accessToken
			return false, status.Error(codes.Unauthenticated, "UNAUTHENTICATED")
		}

		return true, nil
	})

	gatewayLis, grpcServer := createServerWithStreamInterceptor(interceptor.interceptStream)

	go grpcServer.Serve(gatewayLis)
	defer func() {
		grpcServer.Stop()
		_ = gatewayLis.Close()
	}()

	authzServer := mockAuthorizationServer(s.T(), token)
	defer authzServer.Close()

	var credsProvider CredentialsProvider
	credsProvider, err := NewOAuthCredentialsProvider(&OAuthProviderConfig{
		ClientID:               clientID,
		ClientSecret:           clientSecret,
		Audience:               audience,
		AuthorizationServerURL: authzServer.URL,
	})
	s.NoError(err)
	credsProvider = &custom{provider: credsProvider}

	parts := strings.Split(gatewayLis.Addr().String(), ":")
	client, err := NewClient(&ClientConfig{
		GatewayAddress:         fmt.Sprintf("0.0.0.0:%s", parts[len(parts)-1]),
		UsePlaintextConnection: true,
		CredentialsProvider:    credsProvider,
	})
	s.NoError(err)

	// when
	jobs, err := client.NewActivateJobsCommand().JobType("test").MaxJobsToActivate(1).Send(context.Background())

	// then
	s.NoError(err)
	s.GreaterOrEqual(1, credsProvider.(*custom).shouldRetryCalls)
	s.NotEmpty(jobs)
}

type custom struct {
	provider         CredentialsProvider
	shouldRetryCalls int
}

func (c *custom) ApplyCredentials(ctx context.Context, headers map[string]string) error {
	return c.provider.ApplyCredentials(ctx, headers)
}

func (c *custom) ShouldRetryRequest(ctx context.Context, err error) bool {
	retry := c.provider.ShouldRetryRequest(ctx, err)
	if retry {
		c.shouldRetryCalls++
	}

	return retry
}

func mockAuthorizationServer(t *testing.T, token *mutableToken) *httptest.Server {
	return mockAuthorizationServerWithAudience(t, token, audience)
}

func mockAuthorizationServerWithAudience(t *testing.T, token *mutableToken, audience string) *httptest.Server {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		bytes, err := io.ReadAll(request.Body)
		if err != nil {
			panic(err)
		}

		query, err := url.ParseQuery(string(bytes))
		if err != nil {
			panic(err)
		}

		require.Equal(t, "client_credentials", query.Get("grant_type"))
		require.Equal(t, audience, query.Get("audience"))
		require.Equal(t, clientID, query.Get("client_id"))
		require.Equal(t, clientSecret, query.Get("client_secret"))
		require.Regexp(t, regexp.MustCompile(`zeebe-client-go/\d+\.\d+\.\d+.*`),
			request.Header.Get("User-Agent"))

		writer.Header().Set("Content-Type", "application/json")
		responsePayload := []byte("{\"access_token\": \"" + token.value + "\"," +
			"\"expires_in\": 3600," +
			"\"token_type\": \"bearer\"}")

		_, err = writer.Write(responsePayload)
		if err != nil {
			panic(err)
		}
	}))

	return server
}

func createAuthenticatedGrpcServer(validToken string) (net.Listener, *grpc.Server) {
	interceptor := newInterceptor(func(ctx context.Context) (bool, error) {
		if meta, ok := metadata.FromIncomingContext(ctx); ok {
			token := meta["authorization"]
			expected := "Bearer " + validToken
			if token != nil && token[0] == expected {
				return false, nil
			}
		}

		return false, status.Error(codes.Unauthenticated, "UNAUTHENTICATED")
	})

	return createServerWithUnaryInterceptor(interceptor.interceptUnary)
}
