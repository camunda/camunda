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
	"encoding/json"
	"fmt"
	validation "github.com/go-ozzo/ozzo-validation"
	"github.com/go-ozzo/ozzo-validation/is"
	"github.com/pkg/errors"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"io/ioutil"
	"log"
	"net/http"
	"os"
)

const OAuthClientIdEnvVar = "ZEEBE_CLIENT_ID"
const OAuthClientSecretEnvVar = "ZEEBE_CLIENT_SECRET"
const OAuthTokenAudienceEnvVar = "ZEEBE_TOKEN_AUDIENCE"
const OAuthAuthorizationUrlEnvVar = "ZEEBE_AUTHORIZATION_SERVER_URL"

// OAuthDefaultAuthzURL points to the expected default URL for this credentials provider, the Camunda Cloud endpoint.
const OAuthDefaultAuthzURL = "https://login.cloud.camunda.io/oauth/token/"

// OAuthCredentialsProvider is a built-in CredentialsProvider that contains credentials obtained from an OAuth
// authorization server, including a token prefix and an access token. Using these values it sets the 'Authorization'
// header of each gRPC call.
type OAuthCredentialsProvider struct {
	RequestPayload *oauthRequestPayload
	AuthzServerURL string
	Cache          OAuthCredentialsCache

	credentials *OAuthCredentials
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
	// path '$HOME/.camunda/credentials' as default (can be overriden by 'ZEEBE_CLIENT_CONFIG_PATH')
	Cache OAuthCredentialsCache
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
func (provider *OAuthCredentialsProvider) ApplyCredentials(headers map[string]string) {
	credentials := provider.getCredentials()
	if credentials != nil {
		headers["Authorization"] = fmt.Sprintf("%s %s", credentials.TokenType, credentials.AccessToken)
	}
}

// ShouldRetryRequest checks if the error is UNAUTHENTICATED and, if so, attempts to refresh the access token. If the
// new credentials are different from the stored ones, returns true. If the credentials are the same, returns false.
func (provider *OAuthCredentialsProvider) ShouldRetryRequest(err error) bool {
	if status.Code(err) == codes.Unauthenticated {
		return provider.updateCredentials()
	}

	return false
}

// NewOAuthCredentialsProvider requests credentials from an authorization server and uses them to create an OAuthCredentialsProvider.
func NewOAuthCredentialsProvider(config *OAuthProviderConfig) (*OAuthCredentialsProvider, error) {
	applyEnvironmentOverrides(config)
	applyDefaults(config)

	if err := validation.Validate(config.AuthorizationServerURL, is.URL); err != nil {
		return nil, invalidArgumentError("authorization server URL", err.Error())
	} else if err := validation.Validate(config.ClientID, validation.Required); err != nil {
		return nil, invalidArgumentError("client ID", err.Error())
	} else if err := validation.Validate(config.ClientSecret, validation.Required); err != nil {
		return nil, invalidArgumentError("client secret", err.Error())
	} else if err := validation.Validate(config.Audience, validation.Required); err != nil {
		return nil, invalidArgumentError("audience", err.Error())
	} else if err := validation.Validate(config.Cache, validation.Required); err != nil {
		return nil, invalidArgumentError("cache", err.Error())
	}

	payload := &oauthRequestPayload{
		ClientID:     config.ClientID,
		ClientSecret: config.ClientSecret,
		Audience:     config.Audience,
		GrantType:    "client_credentials",
	}

	provider := OAuthCredentialsProvider{RequestPayload: payload, AuthzServerURL: config.AuthorizationServerURL, Cache: config.Cache}
	return &provider, nil
}

func (provider *OAuthCredentialsProvider) getCredentials() *OAuthCredentials {
	if provider.credentials == nil {
		credentials := provider.getCachedCredentials()
		if credentials != nil {
			provider.credentials = credentials
			return credentials
		}
	}

	provider.updateCredentials()
	return provider.credentials
}

func (provider *OAuthCredentialsProvider) updateCredentials() (updated bool) {
	credentials, err := fetchAccessToken(provider.AuthzServerURL, provider.RequestPayload)

	if err != nil {
		log.Printf("Failed while attempting to refresh credentials: %s", err.Error())
	} else if provider.credentials == nil || *(provider.credentials) != *credentials {
		provider.credentials = credentials
		provider.updateCache(credentials)
		return true
	}

	return false
}

func (provider *OAuthCredentialsProvider) updateCache(credentials *OAuthCredentials) {
	audience := provider.RequestPayload.Audience
	err := provider.Cache.Update(audience, credentials)
	if err != nil {
		log.Printf("Failed to persist credentials for %s to cache: %s", audience, err)
	}
}

func (provider *OAuthCredentialsProvider) getCachedCredentials() *OAuthCredentials {
	audience := provider.RequestPayload.Audience
	err := provider.Cache.Refresh()
	if err != nil {
		log.Printf("Failed to refresh the OAuth credentials cache, %s", err.Error())
		return nil
	}
	return provider.Cache.Get(audience)
}

func applyEnvironmentOverrides(config *OAuthProviderConfig) {
	if envClientID := os.Getenv(OAuthClientIdEnvVar); envClientID != "" {
		config.ClientID = envClientID
	}
	if envClientSecret := os.Getenv(OAuthClientSecretEnvVar); envClientSecret != "" {
		config.ClientSecret = envClientSecret
	}
	if envAudience := os.Getenv(OAuthTokenAudienceEnvVar); envAudience != "" {
		config.Audience = envAudience
	}
	if envAuthzServerURL := os.Getenv(OAuthAuthorizationUrlEnvVar); envAuthzServerURL != "" {
		config.AuthorizationServerURL = envAuthzServerURL
	}
}

func applyDefaults(config *OAuthProviderConfig) {
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
}

func fetchAccessToken(authorizationServerURL string, payload *oauthRequestPayload) (*OAuthCredentials, error) {
	jsonPayload, err := json.Marshal(payload)
	if err != nil {
		return nil, err
	}

	reader := bytes.NewReader(jsonPayload)
	response, err := http.Post(authorizationServerURL, "application/json", reader)
	if err != nil {
		return nil, errors.Wrap(err, fmt.Sprintf("failed while requesting access token from URL '%s'", authorizationServerURL))
	}

	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		return nil, errors.New(fmt.Sprintf("access token request failed with status code %d and message %s", response.StatusCode, response.Status))
	}

	jsonResponse, err := ioutil.ReadAll(response.Body)
	if err != nil {
		return nil, errors.Wrap(err, "failed while reading response to access token request")
	}

	responsePayload := &OAuthCredentials{}
	if err := json.Unmarshal(jsonResponse, responsePayload); err != nil {
		return nil, errors.Wrap(err, "failed while unmarshalling access token response from JSON")
	}

	return responsePayload, nil
}

func (config *OAuthProviderConfig) createDefaultCache() (OAuthCredentialsCache, error) {
	return NewOAuthYamlCredentialsCache("")
}
