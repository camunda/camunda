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
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	validation "github.com/go-ozzo/ozzo-validation/v3"
	"github.com/go-ozzo/ozzo-validation/v3/is"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"io/ioutil"
	"log"
	"net/http"
	"strconv"
	"time"
)

const OAuthClientIdEnvVar = "ZEEBE_CLIENT_ID"
const OAuthClientSecretEnvVar = "ZEEBE_CLIENT_SECRET"
const OAuthTokenAudienceEnvVar = "ZEEBE_TOKEN_AUDIENCE"
const OAuthAuthorizationUrlEnvVar = "ZEEBE_AUTHORIZATION_SERVER_URL"
const OAuthRequestTimeoutEnvVar = "ZEEBE_AUTH_REQUEST_TIMEOUT"

// OAuthDefaultAuthzURL points to the expected default URL for this credentials provider, the Camunda Cloud endpoint.
const OAuthDefaultAuthzURL = "https://login.cloud.camunda.io/oauth/token/"

// OAuthDefaultRequestTimeout is the default timeout for OAuth requests
const OAuthDefaultRequestTimeout = 10 * time.Second

// OAuthCredentialsProvider is a built-in CredentialsProvider that contains credentials obtained from an OAuth
// authorization server, including a token prefix and an access token. Using these values it sets the 'Authorization'
// header of each gRPC call.
type OAuthCredentialsProvider struct {
	RequestPayload *oauthRequestPayload
	AuthzServerURL string
	Cache          OAuthCredentialsCache

	credentials *OAuthCredentials
	timeout     time.Duration
}

// OAuthProviderConfig configures an OAuthCredentialsProvider, containing the required data to request an access token
// from an OAuth authorization server which will be appended to each gRPC call's headers.
type OAuthProviderConfig struct {
	// The client identifier used to request an access token. Can be overridden with the environment variable 'ZEEBE_CLIENT_ID'.
	ClientID string
	// The client secret used to request an access token. Can be overridden with the environment variable 'ZEEBE_CLIENT_SECRET'.
	ClientSecret string
	// The audience to which the access token will be sent. Can be overridden with the environment variable 'ZEEBE_TOKEN_AUDIENCE'.
	Audience string
	// The URL for the authorization server from which the access token will be requested. Can be overridden with
	// the environment variable 'ZEEBE_AUTHORIZATION_SERVER_URL'.
	AuthorizationServerURL string
	// Cache to read/write credentials from; if none given, defaults to an oauthYamlCredentialsCache instance with the
	// path '$HOME/.camunda/credentials' as default (can be overridden by 'ZEEBE_CLIENT_CONFIG_PATH')
	Cache OAuthCredentialsCache
	// Timeout is the maximum duration of an OAuth request. The default value is 10 seconds
	Timeout time.Duration
}

// OAuthCredentials contains the data returned by the OAuth authorization server. These credentials are used to modify
// the gRPC call headers.
type OAuthCredentials struct {
	AccessToken string `json:"access_token" yaml:"access_token"`
	ExpiresIn   uint64 `json:"expires_in" yaml:"expires_in"`
	TokenType   string `json:"token_type" yaml:"token_type"`
	Scope       string `json:"scope" yaml:"scope"`
}

type oauthRequestPayload struct {
	ClientID     string `json:"client_id"`
	ClientSecret string `json:"client_secret"`
	Audience     string `json:"audience"`
	GrantType    string `json:"grant_type"`
}

// ApplyCredentials takes a map of headers as input and adds an access token prefixed by a token type to the 'Authorization'
// header of a gRPC call.
func (p *OAuthCredentialsProvider) ApplyCredentials(ctx context.Context, headers map[string]string) {
	credentials := p.getCredentials(ctx)
	if credentials != nil {
		headers["Authorization"] = fmt.Sprintf("%s %s", credentials.TokenType, credentials.AccessToken)
	}
}

// ShouldRetryRequest checks if the error is UNAUTHENTICATED and, if so, attempts to refresh the access token. If the
// new credentials are different from the stored ones, returns true. If the credentials are the same, returns false.
func (p *OAuthCredentialsProvider) ShouldRetryRequest(ctx context.Context, err error) bool {
	if status.Code(err) == codes.Unauthenticated {
		return p.updateCredentials(ctx)
	}

	return false
}

// NewOAuthCredentialsProvider requests credentials from an authorization server and uses them to create an OAuthCredentialsProvider.
func NewOAuthCredentialsProvider(config *OAuthProviderConfig) (*OAuthCredentialsProvider, error) {
	if err := applyCredentialEnvOverrides(config); err != nil {
		return nil, err
	}
	applyCredentialDefaults(config)

	if err := validation.Validate(config.AuthorizationServerURL, is.URL); err != nil {
		return nil, fmt.Errorf("expected to find valid authz server URL '%s': %w", config.AuthorizationServerURL, err)
	} else if err := validation.Validate(config.ClientID, validation.Required); err != nil {
		return nil, fmt.Errorf("expected to find non-empty client id")
	} else if err := validation.Validate(config.ClientSecret, validation.Required); err != nil {
		return nil, fmt.Errorf("expected to find non-empty client secret")
	} else if err := validation.Validate(config.Audience, validation.Required); err != nil {
		return nil, fmt.Errorf("expected to find non-empty audience")
	}

	payload := &oauthRequestPayload{
		ClientID:     config.ClientID,
		ClientSecret: config.ClientSecret,
		Audience:     config.Audience,
		GrantType:    "client_credentials",
	}

	provider := OAuthCredentialsProvider{
		RequestPayload: payload,
		AuthzServerURL: config.AuthorizationServerURL,
		Cache:          config.Cache,
		timeout:        config.Timeout,
	}

	return &provider, nil
}

func (p *OAuthCredentialsProvider) getCredentials(ctx context.Context) *OAuthCredentials {
	if p.credentials == nil {
		credentials := p.getCachedCredentials()
		if credentials != nil {
			p.credentials = credentials
			return credentials
		} else {
			p.updateCredentials(ctx)
		}
	}
	return p.credentials
}

func (p *OAuthCredentialsProvider) updateCredentials(ctx context.Context) (updated bool) {
	credentials, err := p.fetchAccessToken(ctx)
	if err != nil {
		log.Printf("Failed while attempting to refresh credentials: %s", err.Error())
	} else if p.credentials == nil || *(p.credentials) != *credentials {
		p.credentials = credentials
		p.updateCache(credentials)
		return true
	}

	return false
}

func (p *OAuthCredentialsProvider) updateCache(credentials *OAuthCredentials) {
	audience := p.RequestPayload.Audience
	err := p.Cache.Update(audience, credentials)
	if err != nil {
		log.Printf("Failed to persist credentials for %s to cache: %s", audience, err)
	}
}

func (p *OAuthCredentialsProvider) getCachedCredentials() *OAuthCredentials {
	audience := p.RequestPayload.Audience
	err := p.Cache.Refresh()
	if err != nil {
		log.Printf("Failed to refresh the OAuth credentials cache, %s", err.Error())
		return nil
	}
	return p.Cache.Get(audience)
}

func applyCredentialEnvOverrides(config *OAuthProviderConfig) error {
	if envClientID := env.get(OAuthClientIdEnvVar); envClientID != "" {
		config.ClientID = envClientID
	}
	if envClientSecret := env.get(OAuthClientSecretEnvVar); envClientSecret != "" {
		config.ClientSecret = envClientSecret
	}
	if envAudience := env.get(OAuthTokenAudienceEnvVar); envAudience != "" {
		config.Audience = envAudience
	}
	if envAuthzServerURL := env.get(OAuthAuthorizationUrlEnvVar); envAuthzServerURL != "" {
		config.AuthorizationServerURL = envAuthzServerURL
	}
	if envOAuthReqTimeout := env.get(OAuthRequestTimeoutEnvVar); envOAuthReqTimeout != "" {
		timeout, err := strconv.ParseUint(envOAuthReqTimeout, 10, 64)
		if err != nil {
			return fmt.Errorf("could not parse value of %s, should be non-negative amount: %w", OAuthRequestTimeoutEnvVar, err)
		}
		config.Timeout = time.Duration(timeout) * time.Millisecond
	}

	return nil
}

func applyCredentialDefaults(config *OAuthProviderConfig) {
	if config.AuthorizationServerURL == "" {
		config.AuthorizationServerURL = OAuthDefaultAuthzURL
	}

	if config.Cache == nil {
		cache, err := NewOAuthYamlCredentialsCache("")
		if err != nil {
			log.Printf("Failed to create OAuth YAML credentials cache with default path: %s", err.Error())
		} else {
			config.Cache = cache
		}
	}

	if config.Timeout <= time.Duration(0) {
		config.Timeout = OAuthDefaultRequestTimeout
	}
}

func (p *OAuthCredentialsProvider) fetchAccessToken(ctx context.Context) (*OAuthCredentials, error) {
	req, cancel, err := p.buildOAuthRequest(ctx)
	if err != nil {
		return nil, err
	}
	defer cancel()

	response, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed while requesting access token: %w", err)
	}

	defer func() {
		if err := response.Body.Close(); err != nil {
			log.Printf("couldn't close OAuth response body, connection may be hung: %s\n", err.Error())
		}
	}()

	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("access token request failed with status code %d and message %s", response.StatusCode, response.Status)
	}

	jsonResponse, err := ioutil.ReadAll(response.Body)
	if err != nil {
		return nil, fmt.Errorf("failed while reading response to access token request: %w", err)
	}

	responsePayload := &OAuthCredentials{}
	if err := json.Unmarshal(jsonResponse, responsePayload); err != nil {
		return nil, fmt.Errorf("failed while unmarshalling access token response from JSON: %w", err)
	}

	return responsePayload, nil
}

func (p *OAuthCredentialsProvider) buildOAuthRequest(ctx context.Context) (*http.Request, context.CancelFunc, error) {
	jsonPayload, err := json.Marshal(p.RequestPayload)
	if err != nil {
		return nil, nil, err
	}
	reader := bytes.NewReader(jsonPayload)

	ctx, cancel := context.WithTimeout(ctx, p.timeout)

	req, err := http.NewRequestWithContext(ctx, "POST", p.AuthzServerURL, reader)
	if err != nil {
		cancel()
		return nil, nil, fmt.Errorf("failed while building request: %w", err)
	}

	return req, cancel, nil
}
