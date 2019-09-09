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
	"github.com/pkg/errors"
	"github.com/stretchr/testify/require"
	"golang.org/x/net/context"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
)

type mutableToken struct {
	value string
}

func TestOAuthCredentialsProvider(t *testing.T) {
	// given
	interceptor := newRecordingInterceptor(nil)
	gatewayLis, grpcServer := createServerWithInterceptor(interceptor.unaryClientInterceptor)

	go grpcServer.Serve(gatewayLis)
	defer func() {
		grpcServer.Stop()
		_ = gatewayLis.Close()
	}()

	authzServer := mockAuthorizationServer(t, &mutableToken{value: accessToken})
	defer authzServer.Close()

	credsProvider, err := NewOAuthCredentialsProvider(&OAuthProviderConfig{
		ClientID:               clientID,
		ClientSecret:           clientSecret,
		Audience:               audience,
		AuthorizationServerURL: authzServer.URL,
	})

	require.NoError(t, err)
	parts := strings.Split(gatewayLis.Addr().String(), ":")
	client, err := NewZBClient(&ZBClientConfig{
		GatewayAddress:         fmt.Sprintf("0.0.0.0:%s", parts[len(parts)-1]),
		UsePlaintextConnection: true,
		CredentialsProvider:    credsProvider,
	})
	require.NoError(t, err)

	// when
	_, err = client.NewTopologyCommand().Send()

	// then
	require.Error(t, err)
	if errorStatus, ok := status.FromError(err); ok {
		require.Equal(t, codes.Unimplemented, errorStatus.Code())
	}
	require.Equal(t, tokenType+" "+accessToken, interceptor.authHeader)
}

func TestOAuthProviderRetry(t *testing.T) {
	// given
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

	authzServer := mockAuthorizationServer(t, token)
	defer authzServer.Close()

	credsProvider, err := NewOAuthCredentialsProvider(&OAuthProviderConfig{
		ClientID:               clientID,
		ClientSecret:           clientSecret,
		Audience:               audience,
		AuthorizationServerURL: authzServer.URL,
	})

	require.NoError(t, err)
	parts := strings.Split(gatewayLis.Addr().String(), ":")
	client, err := NewZBClient(&ZBClientConfig{
		GatewayAddress:         fmt.Sprintf("0.0.0.0:%s", parts[len(parts)-1]),
		UsePlaintextConnection: true,
		CredentialsProvider:    credsProvider,
	})
	require.NoError(t, err)

	// when
	_, err = client.NewTopologyCommand().Send()

	// then
	require.Error(t, err)
	if errorStatus, ok := status.FromError(err); ok {
		require.Equal(t, codes.Unimplemented, errorStatus.Code())
	}
	require.EqualValues(t, 2, interceptor.interceptCounter)
}

func TestNotRetryWithSameCredentials(t *testing.T) {
	// given
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

	authzServer := mockAuthorizationServer(t, token)
	defer authzServer.Close()

	credsProvider, err := NewOAuthCredentialsProvider(&OAuthProviderConfig{
		ClientID:               clientID,
		ClientSecret:           clientSecret,
		Audience:               audience,
		AuthorizationServerURL: authzServer.URL,
	})

	require.NoError(t, err)
	parts := strings.Split(gatewayLis.Addr().String(), ":")
	client, err := NewZBClient(&ZBClientConfig{
		GatewayAddress:         fmt.Sprintf("0.0.0.0:%s", parts[len(parts)-1]),
		UsePlaintextConnection: true,
		CredentialsProvider:    credsProvider,
	})
	require.NoError(t, err)

	// when
	_, err = client.NewTopologyCommand().Send()

	// then
	require.Error(t, err)
	if errorStatus, ok := status.FromError(err); ok {
		require.Equal(t, codes.Unauthenticated, errorStatus.Code())
	}
	require.EqualValues(t, 1, interceptor.interceptCounter)
}

var configErrorTests = []struct {
	name       string
	config     *OAuthProviderConfig
	err        ZBError
	errMessage string
}{
	{
		"malformed authorization server URL",
		&OAuthProviderConfig{
			ClientID:               clientID,
			ClientSecret:           clientSecret,
			Audience:               audience,
			AuthorizationServerURL: "foo",
		},
		InvalidArgumentError,
		invalidArgumentError("authorization server URL", "must be a valid URL").Error(),
	},
	{
		"missing client id",
		&OAuthProviderConfig{

			ClientSecret:           clientSecret,
			Audience:               audience,
			AuthorizationServerURL: "http://foo",
		},
		InvalidArgumentError,
		invalidArgumentError("client ID", "cannot be blank").Error(),
	},
	{
		"missing client secret",
		&OAuthProviderConfig{
			ClientID:               clientID,
			Audience:               audience,
			AuthorizationServerURL: "http://foo",
		},
		InvalidArgumentError,
		invalidArgumentError("client secret", "cannot be blank").Error(),
	},
	{
		"missing audience",
		&OAuthProviderConfig{
			ClientID:               clientID,
			ClientSecret:           clientSecret,
			AuthorizationServerURL: "http://foo",
		},
		InvalidArgumentError,
		invalidArgumentError("audience", "cannot be blank").Error(),
	},
}

func TestInvalidOAuthProviderConfigurations(t *testing.T) {
	for _, test := range configErrorTests {
		t.Run(test.name, func(t *testing.T) {
			// when
			_, err := NewOAuthCredentialsProvider(test.config)

			//then
			require.EqualValues(t, test.err, errors.Cause(err))
			require.EqualValues(t, test.errMessage, err.Error())
		})
	}
}

type fieldExtractor func(config *OAuthProviderConfig) string

var envVarTests = []struct {
	name           string
	envVar         string
	value          string
	fieldExtractor fieldExtractor
}{
	{
		"environment variable client id",
		"ZEEBE_CLIENT_ID",
		"envClient",
		func(c *OAuthProviderConfig) string { return c.ClientID },
	},
	{
		"environment variable client secret",
		"ZEEBE_CLIENT_SECRET",
		"envSecret",
		func(c *OAuthProviderConfig) string { return c.ClientSecret },
	},
	{
		"environment variable audience",
		"ZEEBE_TOKEN_AUDIENCE",
		"envAudience",
		func(c *OAuthProviderConfig) string { return c.Audience },
	},
	{
		"environment variable authorization server URL",
		"ZEEBE_AUTHORIZATION_SERVER_URL",
		"https://envAuthzUrl",
		func(c *OAuthProviderConfig) string { return c.AuthorizationServerURL },
	},
}

func TestOAuthProviderWithEnvVars(t *testing.T) {
	for _, test := range envVarTests {
		t.Run(test.name, func(t *testing.T) {
			if err := os.Setenv(test.envVar, test.value); err != nil {
				panic(err)
			}

			config := &OAuthProviderConfig{
				ClientID:               clientID,
				ClientSecret:           clientSecret,
				Audience:               audience,
				AuthorizationServerURL: "http://foo",
			}

			// when
			_, _ = NewOAuthCredentialsProvider(config)

			// then
			require.EqualValues(t, test.value, test.fieldExtractor(config))
		})
		if err := os.Unsetenv(test.envVar); err != nil {
			panic(err)
		}
	}
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
