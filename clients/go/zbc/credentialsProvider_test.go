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
	"context"
	"encoding/json"
	"fmt"
	"github.com/pkg/errors"
	"github.com/stretchr/testify/require"
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
	"io/ioutil"
	"net"
	"net/http"
	"os"
	"strings"
	"testing"
)

// request data
const clientId = "someClient"
const clientSecret = "someSecret"
const audience = "localhost"

// response data
const accessToken = "someToken"
const tokenType = "Bearer"

func TestOAuthCredentialsProvider(t *testing.T) {
	// given
	interceptor := &recordingInterceptor{}
	gatewayLis, grpcServer := createServerWithInterceptor(interceptor.unaryClientInterceptor)

	go grpcServer.Serve(gatewayLis)
	defer func() {
		grpcServer.Stop()
		_ = gatewayLis.Close()
	}()

	authServerLis := createMockAuthorizationServer(t)
	defer authServerLis.Close()

	authServerUrl := fmt.Sprintf("http://%s/oauth/token", authServerLis.Addr().String())
	credsProvider, err := NewOAuthCredentialsProvider(&OAuthProviderConfig{
		ClientId:               clientId,
		ClientSecret:           clientSecret,
		Audience:               audience,
		AuthorizationServerUrl: authServerUrl,
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
	require.Equal(t, tokenType+" "+accessToken, interceptor.capturedToken)
}

var configErrorTests = []struct {
	name       string
	config     *OAuthProviderConfig
	err        ZBError
	errMessage string
}{
	{"missing authorization server URL",
		&OAuthProviderConfig{
			ClientId:     clientId,
			ClientSecret: clientSecret,
			Audience:     audience,
		},
		InvalidArgumentError,
		invalidArgumentError("authorization server URL", "cannot be blank").Error(),
	},
	{
		"malformed authorization server URL",
		&OAuthProviderConfig{
			ClientId:               clientId,
			ClientSecret:           clientSecret,
			Audience:               audience,
			AuthorizationServerUrl: "foo",
		},
		InvalidArgumentError,
		invalidArgumentError("authorization server URL", "must be a valid URL").Error(),
	},
	{
		"missing client id",
		&OAuthProviderConfig{

			ClientSecret:           clientSecret,
			Audience:               audience,
			AuthorizationServerUrl: "http://foo",
		},
		InvalidArgumentError,
		invalidArgumentError("client ID", "cannot be blank").Error(),
	},
	{
		"missing client secret",
		&OAuthProviderConfig{
			ClientId:               clientId,
			Audience:               audience,
			AuthorizationServerUrl: "http://foo",
		},
		InvalidArgumentError,
		invalidArgumentError("client secret", "cannot be blank").Error(),
	},
	{
		"missing audience",
		&OAuthProviderConfig{
			ClientId:               clientId,
			ClientSecret:           clientSecret,
			AuthorizationServerUrl: "http://foo",
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
		func(c *OAuthProviderConfig) string { return c.ClientId },
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
		func(c *OAuthProviderConfig) string { return c.AuthorizationServerUrl },
	},
}

func TestOAuthProviderWithEnvVars(t *testing.T) {
	for _, test := range envVarTests {
		t.Run(test.name, func(t *testing.T) {
			if err := os.Setenv(test.envVar, test.value); err != nil {
				panic(err)
			}

			config := &OAuthProviderConfig{
				ClientId:               clientId,
				ClientSecret:           clientSecret,
				Audience:               audience,
				AuthorizationServerUrl: "http://foo",
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

type CustomCredentialsProvider struct {
	customToken string
}

func (t CustomCredentialsProvider) ApplyCredentials(headers map[string]string) {
	headers["Authorization"] = t.customToken
}

func TestCustomCredentialsProvider(t *testing.T) {
	// given
	interceptor := &recordingInterceptor{}
	lis, grpcServer := createServerWithInterceptor(interceptor.unaryClientInterceptor)

	go grpcServer.Serve(lis)
	defer func() {
		grpcServer.Stop()
		_ = lis.Close()
	}()

	credsProvider := &CustomCredentialsProvider{customToken: accessToken}

	parts := strings.Split(lis.Addr().String(), ":")

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
	require.Equal(t, accessToken, interceptor.capturedToken)
}

func createMockAuthorizationServer(t *testing.T) net.Listener {
	http.HandleFunc("/oauth/token", func(writer http.ResponseWriter, request *http.Request) {
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
			ClientId:     clientId,
			ClientSecret: clientSecret,
			Audience:     audience,
			GrantType:    "client_credentials",
		}, requestPayload)

		writer.WriteHeader(200)
		responsePayload := []byte("{\"access_token\": \"" + accessToken + "\"," +
			"\"expires_in\": 3600," +
			"\"token_type\": \"" + tokenType + "\"," +
			"\"scope\": \"grpc\"}")

		_, err = writer.Write(responsePayload)
		if err != nil {
			panic(err)
		}
	})
	listener, err := net.Listen("tcp", ":0")
	if err != nil {
		panic(err)
	}

	go func() {
		_ = http.Serve(listener, nil)
	}()

	return listener
}

func createServerWithInterceptor(interceptorFunc grpc.UnaryServerInterceptor) (net.Listener, *grpc.Server) {
	listener, _ := net.Listen("tcp", "0.0.0.0:0")

	grpcServer := grpc.NewServer(grpc.UnaryInterceptor(interceptorFunc))
	pb.RegisterGatewayServer(grpcServer, &pb.UnimplementedGatewayServer{})

	return listener, grpcServer
}

type recordingInterceptor struct {
	capturedToken string
}

func (interceptor *recordingInterceptor) unaryClientInterceptor(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (resp interface{}, err error) {
	headers, success := metadata.FromIncomingContext(ctx)

	if !success {
		return nil, errors.New("recording interceptor failed at retrieving headers")
	}

	interceptor.capturedToken = headers.Get("Authorization")[0]

	return handler(ctx, req)
}
