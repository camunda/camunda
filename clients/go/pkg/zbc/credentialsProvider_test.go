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
	"errors"
	"fmt"
	"github.com/stretchr/testify/require"
	"github.com/zeebe-io/zeebe/clients/go/pkg/pb"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
	"net"
	"strings"
	"testing"
)

// request data
const clientID = "someClient"
const clientSecret = "someSecret"
const audience = "localhost"

// response data
const accessToken = "someToken"
const tokenType = "Bearer"

type customCredentialsProvider struct {
	customToken    string
	retryPredicate func(error) bool
}

func (t customCredentialsProvider) ApplyCredentials(_ context.Context, headers map[string]string) {
	headers["Authorization"] = t.customToken
}

func (t customCredentialsProvider) ShouldRetryRequest(_ context.Context, err error) bool {
	if t.retryPredicate != nil {
		return t.retryPredicate(err)
	}

	return false
}

func TestCustomCredentialsProvider(t *testing.T) {
	// given
	interceptor := newRecordingInterceptor(nil)
	lis, grpcServer := createServerWithInterceptor(interceptor.unaryClientInterceptor)

	go grpcServer.Serve(lis)
	defer func() {
		grpcServer.Stop()
		_ = lis.Close()
	}()

	credsProvider := &customCredentialsProvider{customToken: accessToken}

	parts := strings.Split(lis.Addr().String(), ":")

	client, err := NewClient(&ClientConfig{
		GatewayAddress:         fmt.Sprintf("0.0.0.0:%s", parts[len(parts)-1]),
		UsePlaintextConnection: true,
		CredentialsProvider:    credsProvider,
	})
	require.NoError(t, err)

	// when
	_, err = client.NewTopologyCommand().Send(context.Background())

	// then
	require.Error(t, err)
	if errorStatus, ok := status.FromError(err); ok {
		require.Equal(t, codes.Unimplemented, errorStatus.Code())
	}
	require.Equal(t, accessToken, interceptor.authHeader)
}

func TestRetryMoreThanOnce(t *testing.T) {
	// given
	retries := 2
	interceptor := newRecordingInterceptor(func(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
		return nil, status.Error(codes.Unknown, "expected")
	})

	lis, grpcServer := createServerWithInterceptor(interceptor.unaryClientInterceptor)

	go grpcServer.Serve(lis)
	defer func() {
		grpcServer.Stop()
		_ = lis.Close()
	}()

	provider := &customCredentialsProvider{
		customToken: accessToken,
		retryPredicate: func(e error) bool {
			retries--
			return retries >= 0
		},
	}

	parts := strings.Split(lis.Addr().String(), ":")
	client, err := NewClient(&ClientConfig{
		GatewayAddress:         fmt.Sprintf("0.0.0.0:%s", parts[len(parts)-1]),
		UsePlaintextConnection: true,
		CredentialsProvider:    provider,
	})
	require.NoError(t, err)

	// when
	_, err = client.NewTopologyCommand().Send(context.Background())

	// then
	require.Error(t, err)
	if errorStatus, ok := status.FromError(err); ok {
		require.Equal(t, codes.Unknown, errorStatus.Code())
	}
	require.EqualValues(t, 3, interceptor.interceptCounter)
}

func TestNoRetryWithoutProvider(t *testing.T) {
	// given
	interceptor := newRecordingInterceptor(func(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
		return nil, status.Error(codes.Unauthenticated, "expected")
	})

	lis, grpcServer := createServerWithInterceptor(interceptor.unaryClientInterceptor)

	go grpcServer.Serve(lis)
	defer func() {
		grpcServer.Stop()
		_ = lis.Close()
	}()

	parts := strings.Split(lis.Addr().String(), ":")
	client, err := NewClient(&ClientConfig{
		GatewayAddress:         fmt.Sprintf("0.0.0.0:%s", parts[len(parts)-1]),
		UsePlaintextConnection: true,
		CredentialsProvider:    nil,
	})
	require.NoError(t, err)

	// when
	_, err = client.NewTopologyCommand().Send(context.Background())

	// then
	require.Error(t, err)
	if errorStatus, ok := status.FromError(err); ok {
		require.Equal(t, codes.Unauthenticated, errorStatus.Code())
	}
	require.EqualValues(t, 1, interceptor.interceptCounter)
}

func createServerWithInterceptor(interceptorFunc grpc.UnaryServerInterceptor) (net.Listener, *grpc.Server) {
	listener, _ := net.Listen("tcp", "0.0.0.0:0")

	grpcServer := grpc.NewServer(grpc.UnaryInterceptor(interceptorFunc))
	pb.RegisterGatewayServer(grpcServer, &pb.UnimplementedGatewayServer{})

	return listener, grpcServer
}

// recordingInterceptor has several features. For each intercepted gRPC call, it will:
//  * capture the authorization header (allowing you to check  its value)
//  * execute an interceptAction, if any exists (allowing you to reject calls with specific codes, modify headers, etc)
//  * increment an intercept counter (allowing you to verify how many calls were made)
type recordingInterceptor struct {
	authHeader       string
	interceptAction  interceptFunc
	interceptCounter int
}

type interceptFunc func(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error)

func (interceptor *recordingInterceptor) unaryClientInterceptor(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (resp interface{}, err error) {
	interceptor.interceptCounter++

	headers, success := metadata.FromIncomingContext(ctx)
	if !success {
		return nil, errors.New("recording interceptor failed at retrieving headers")
	}

	interceptor.authHeader = strings.Join(headers.Get("Authorization"), "")

	if interceptor.interceptAction != nil {
		return interceptor.interceptAction(ctx, req, info, handler)
	}

	return handler(ctx, req)
}

// Creates a new recording interceptor with a given interception function
func newRecordingInterceptor(action interceptFunc) *recordingInterceptor {
	interceptor := &recordingInterceptor{}
	interceptor.interceptAction = action

	return interceptor
}
