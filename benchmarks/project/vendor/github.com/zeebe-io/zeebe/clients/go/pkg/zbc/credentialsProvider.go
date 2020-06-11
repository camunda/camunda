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

import "context"

// CredentialsProvider is responsible for adding credentials to each gRPC call's headers.
type CredentialsProvider interface {
	// Takes a map of gRPC headers as defined in credentials.PerRPCCredentials and adds credentials to them.
	ApplyCredentials(ctx context.Context, headers map[string]string) error
	// Returns true if the request should be retried, false otherwise.
	ShouldRetryRequest(ctx context.Context, err error) bool
}

// noopCredentialsProvider implements the CredentialsProvider interface but doesn't modify the authorization headers and
// doesn't retry requests in case of failure.
type noopCredentialsProvider struct{}

// ApplyCredentials does nothing.
func (noopCredentialsProvider) ApplyCredentials(_ context.Context, _ map[string]string) error {
	return nil
}

// ShouldRetryRequest always returns false.
func (noopCredentialsProvider) ShouldRetryRequest(_ context.Context, _ error) bool {
	return false
}
