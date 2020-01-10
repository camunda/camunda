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
	"encoding/json"
	"fmt"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	"golang.org/x/net/context"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
	"io/ioutil"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
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
	interceptor := newRecordingInterceptor(nil)
	gatewayLis, grpcServer := createServerWithInterceptor(interceptor.unaryClientInterceptor)

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
	_, err = client.NewTopologyCommand().Send()

	// then
	s.Error(err)
	if errorStatus, ok := status.FromError(err); ok {
		s.Equal(codes.Unimplemented, errorStatus.Code())
	}
	s.Equal(tokenType+" "+accessToken, interceptor.authHeader)
}

func (s *oauthCredsProviderTestSuite) TestOAuthProviderRetry() {
	// given
	truncateDefaultOAuthYamlCacheFile()
	token := &mutableToken{value: "firstToken"}
	first := true
	interceptor := newRecordingInterceptor(func(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
		if first {
			first = false
			token.value = accessToken
			return nil, status.Error(codes.Unauthenticated, "UNAUTHENTICATED")
		}

		return handler(ctx, req)
	})

	gatewayLis, grpcServer := createServerWithInterceptor(interceptor.unaryClientInterceptor)

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
	_, err = client.NewTopologyCommand().Send()

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

	interceptor := newRecordingInterceptor(func(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
		return nil, status.Error(codes.Unauthenticated, "UNAUTHENTICATED")
	})

	gatewayLis, grpcServer := createServerWithInterceptor(interceptor.unaryClientInterceptor)

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
	_, err = client.NewTopologyCommand().Send()

	// then
	s.Error(err)
	if errorStatus, ok := status.FromError(err); ok {
		s.Equal(codes.Unauthenticated, errorStatus.Code())
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

			//then
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
	_, err = client.NewTopologyCommand().Send()

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
	err = cache.Update(audience, &OAuthCredentials{
		AccessToken: accessToken,
		ExpiresIn:   3600,
		TokenType:   "Bearer",
		Scope:       "grpc",
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
	_, err = client.NewTopologyCommand().Send()

	// then
	s.NoError(err)
	if errorStatus, ok := status.FromError(err); ok {
		s.Equal(codes.OK, errorStatus.Code())
	}

	s.False(authServerCalled)

	// when we do it again
	_, err = client.NewTopologyCommand().Send()

	// then
	s.NoError(err)
	if errorStatus, ok := status.FromError(err); ok {
		s.Equal(codes.OK, errorStatus.Code())
	}

	s.False(authServerCalled)
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
	client.SetRequestTimeout(time.Hour)

	// when
	finishCmd := make(chan struct{})
	go func() {
		_, err = client.NewTopologyCommand().Send()
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

	s.EqualValues(codes.Unauthenticated, status.Code(err))
	// TODO(MiguelPires): uncomment after https://github.com/zeebe-io/zeebe/issues/3536
	//  s.True(errors.Is(err, context.DeadlineExceeded))
}

func mockAuthorizationServer(t *testing.T, token *mutableToken) *httptest.Server {
	return mockAuthorizationServerWithAudience(t, token, audience)
}

func mockAuthorizationServerWithAudience(t *testing.T, token *mutableToken, audience string) *httptest.Server {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		bytes, err := ioutil.ReadAll(request.Body)
		if err != nil {
			panic(err)
		}

		requestPayload := &oauthRequestPayload{}
		err = json.Unmarshal(bytes, requestPayload)
		if err != nil {
			panic(err)
		}

		require.EqualValues(t, &oauthRequestPayload{
			ClientID:     clientID,
			ClientSecret: clientSecret,
			Audience:     audience,
			GrantType:    "client_credentials",
		}, requestPayload)

		writer.WriteHeader(200)
		responsePayload := []byte("{\"access_token\": \"" + token.value + "\"," +
			"\"expires_in\": 3600," +
			"\"token_type\": \"" + tokenType + "\"," +
			"\"scope\": \"grpc\"}")

		_, err = writer.Write(responsePayload)
		if err != nil {
			panic(err)
		}
	}))

	return server
}

func createAuthenticatedGrpcServer(validToken string) (net.Listener, *grpc.Server) {
	interceptor := newRecordingInterceptor(func(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
		if meta, ok := metadata.FromIncomingContext(ctx); ok {
			token := meta["authorization"]
			expected := "Bearer " + validToken
			if token != nil && token[0] == expected {
				return nil, nil
			}
		}

		return nil, status.Error(codes.Unauthenticated, "UNAUTHENTICATED")
	})

	return createServerWithInterceptor(interceptor.unaryClientInterceptor)
}
