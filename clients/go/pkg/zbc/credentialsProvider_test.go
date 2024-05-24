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
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
	"github.com/stretchr/testify/require"
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

type customCredentialsProvider struct {
	customToken    string
	retryPredicate func(error) bool
}

func (t customCredentialsProvider) ApplyCredentials(_ context.Context, headers map[string]string) error {
	headers["Authorization"] = t.customToken
	return nil
}

func (t customCredentialsProvider) ShouldRetryRequest(_ context.Context, err error) bool {
	if t.retryPredicate != nil {
		return t.retryPredicate(err)
	}

	return false
}

func TestCustomCredentialsProvider(t *testing.T) {
	// given
	interceptor := newInterceptor(nil)
	lis, grpcServer := createServerWithUnaryInterceptor(interceptor.interceptUnary)

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
	interceptor := newInterceptor(func(ctx context.Context) (bool, error) {
		return false, status.Error(codes.Unknown, "expected")
	})

	lis, grpcServer := createServerWithUnaryInterceptor(interceptor.interceptUnary)

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
	interceptor := newInterceptor(func(ctx context.Context) (bool, error) {
		return false, status.Error(codes.Unauthenticated, "expected")
	})

	lis, grpcServer := createServerWithUnaryInterceptor(interceptor.interceptUnary)

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

func createServerWithUnaryInterceptor(interceptorFunc grpc.UnaryServerInterceptor) (net.Listener, *grpc.Server) {
	listener, _ := net.Listen("tcp", "0.0.0.0:0")

	grpcServer := grpc.NewServer(grpc.UnaryInterceptor(interceptorFunc))
	pb.RegisterGatewayServer(grpcServer, &pb.UnimplementedGatewayServer{})

	return listener, grpcServer
}

func createServerWithStreamInterceptor(interceptorFunc grpc.StreamServerInterceptor) (net.Listener, *grpc.Server) {
	listener, _ := net.Listen("tcp", "0.0.0.0:0")

	grpcServer := grpc.NewServer(grpc.StreamInterceptor(interceptorFunc))
	pb.RegisterGatewayServer(grpcServer, &dummyGateway{})

	return listener, grpcServer
}

type dummyGateway struct {
	pb.UnimplementedGatewayServer
}

func (d *dummyGateway) ActivateJobs(_ *pb.ActivateJobsRequest, s pb.Gateway_ActivateJobsServer) error {
	jobs := []*pb.ActivatedJob{{Key: 0}}
	return s.SendMsg(&pb.ActivateJobsResponse{Jobs: jobs})
}

// recordingInterceptor has several features. For each intercepted gRPC call, it will:
//   - capture the authorization header (allowing you to check  its value)
//   - execute an interceptAction, if any exists (allowing you to reject calls with specific codes, modify headers, etc)
//   - increment an intercept counter (allowing you to verify how many calls were made)
type recordingInterceptor struct {
	authHeader       string
	interceptAction  interceptFunc
	interceptCounter int
}

// the interceptFunc returns a boolean and an error. If the error is nil and the boolean is true, the request will be
// passed to the handler. If the error is non-nil or the boolean is false, the request will not be passed to the handler
// and the error value will be returned
type interceptFunc func(ctx context.Context) (bool, error)

func (i *recordingInterceptor) interceptUnary(ctx context.Context, req interface{}, _ *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (resp interface{}, err error) {
	i.interceptCounter++

	if handle, err := i.interceptCall(ctx); err != nil || !handle {
		return nil, err
	}

	return handler(ctx, req)
}

func (i *recordingInterceptor) interceptStream(srv interface{}, ss grpc.ServerStream, _ *grpc.StreamServerInfo, handler grpc.StreamHandler) error {
	i.interceptCounter++

	ctx := ss.Context()
	if handle, err := i.interceptCall(ctx); err != nil || !handle {
		return err
	}

	return handler(srv, ss)
}

func (i *recordingInterceptor) interceptCall(ctx context.Context) (bool, error) {
	headers, success := metadata.FromIncomingContext(ctx)
	if !success {
		return true, errors.New("recording interceptor failed at retrieving headers")
	}

	i.authHeader = strings.Join(headers.Get("Authorization"), "")

	if i.interceptAction != nil {
		return i.interceptAction(ctx)
	}

	return true, nil
}

// Creates a new recording interceptor with a given interception function
func newInterceptor(action interceptFunc) *recordingInterceptor {
	interceptor := &recordingInterceptor{}
	interceptor.interceptAction = action

	return interceptor
}
