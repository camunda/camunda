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
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
	"net"
	"strings"
	"testing"
)

func TestZeebeClientCredentialsProvider(t *testing.T) {
	// given
	interceptor := &recordingInterceptor{}
	lis, grpcServer := createServerWithInterceptor(interceptor.unaryClientInterceptor)

	go grpcServer.Serve(lis)
	defer func() {
		grpcServer.Stop()
		_ = lis.Close()
	}()

	credsProvider, err := NewZeebeClientCredentialsProvider("../resources/creds")
	require.NoError(t, err)

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
	require.Equal(t,
		"Bearer expectedToken", interceptor.capturedToken)
}

func TestZeebeClientCredentialsProviderWithEmptyPath(t *testing.T) {
	// when
	_, err := NewZeebeClientCredentialsProvider("")

	// then
	require.EqualError(t, err, newEmptyPathError("Zeebe credentials file").Error())
}

func TestZeebeClientCredentialsProviderWithWrongPath(t *testing.T) {
	// when
	wrongPath := "../resources/creds_wrong_path"
	_, err := NewZeebeClientCredentialsProvider(wrongPath)

	// then
	require.EqualError(t, err, newNoSuchFileError("Zeebe credentials file", wrongPath).Error())
}

type CustomCredentialsProvider struct {
	customToken string
}

func (t CustomCredentialsProvider) ApplyCredentials(headers map[string]string) {
	headers["Authorization"] = t.customToken
}

func TestCustomCredentialsProvider(t *testing.T) {
	// given
	const token = "someToken"
	interceptor := &recordingInterceptor{}
	lis, grpcServer := createServerWithInterceptor(interceptor.unaryClientInterceptor)

	go grpcServer.Serve(lis)
	defer func() {
		grpcServer.Stop()
		_ = lis.Close()
	}()

	credsProvider := &CustomCredentialsProvider{customToken: token}

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
	require.Equal(t, token, interceptor.capturedToken)
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
