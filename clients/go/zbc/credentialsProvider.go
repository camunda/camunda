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
	"github.com/go-ozzo/ozzo-validation"
	"github.com/go-ozzo/ozzo-validation/is"
	"github.com/pkg/errors"
	"io/ioutil"
	"net/http"
	"os"
)

// CredentialsProvider is responsible for adding credentials to each gRPC call's headers.
type CredentialsProvider interface {
	// Takes a map of gRPC headers as defined in credentials.PerRPCCredentials and adds credentials to them
	ApplyCredentials(headers map[string]string)
}

// A built-in CredentialsProvider that contains contains credentials obtained from an OAuth authorization server,
// including a token prefix and an access token. Using these values it sets the 'Authorization' header of each gRPC call.
type OAuthCredentialsProvider struct {
	Credentials *OauthCredentials
}

// Configuration data for the OAuthCredentialsProvider, containing the required data to request an access token from
// an OAuth authorization server which will be appended to each gRPC call's headers.
type OAuthProviderConfig struct {
	// The client identifier used to request an access token. Can be overridden with the environment variable 'ZEEBE_CLIENT_ID'.
	ClientId string
	// The client secret used to request an access token. Can be overridden with the environment variable 'ZEEBE_CLIENT_SECRET'.
	ClientSecret string
	// The audience to which the access token will be sent. Can be overridden with the environment variable 'ZEEBE_TOKEN_AUDIENCE'.
	Audience string
	// The URL for the authorization server from which the access token will be requested. Can be overridden with
	// the environment variable 'ZEEBE_AUTHORIZATION_SERVER_URL'.
	AuthorizationServerUrl string
}

type OauthCredentials struct {
	AccessToken string `json:"access_token"`
	ExpiresIn   uint64 `json:"expires_in"`
	TokenType   string `json:"token_type"`
	Scope       string `json:"scope"`
}

type oauthRequestPayload struct {
	ClientId     string `json:"client_id"`
	ClientSecret string `json:"client_secret"`
	Audience     string `json:"audience"`
	GrantType    string `json:"grant_type"`
}

// Takes a map of headers as input and adds an access token prefixed by a token type to the 'Authorization'
// header of a gRPC call.
func (provider *OAuthCredentialsProvider) ApplyCredentials(headers map[string]string) {
	headers["Authorization"] = fmt.Sprintf("%s %s", provider.Credentials.TokenType, provider.Credentials.AccessToken)
}

// Requests credentials from an authorization server which are then used to create an OAuthCredentialsProvider.
func NewOAuthCredentialsProvider(config *OAuthProviderConfig) (*OAuthCredentialsProvider, error) {
	applyEnvironmentOverrides(config)

	if err := validation.Validate(config.AuthorizationServerUrl, validation.Required, is.URL); err != nil {
		return nil, invalidArgumentError("authorization server URL", err.Error())
	} else if err := validation.Validate(config.ClientId, validation.Required); err != nil {
		return nil, invalidArgumentError("client ID", err.Error())
	} else if err := validation.Validate(config.ClientSecret, validation.Required); err != nil {
		return nil, invalidArgumentError("client secret", err.Error())
	} else if err := validation.Validate(config.Audience, validation.Required); err != nil {
		return nil, invalidArgumentError("audience", err.Error())
	}

	payload := &oauthRequestPayload{
		ClientId:     config.ClientId,
		ClientSecret: config.ClientSecret,
		Audience:     config.Audience,
		GrantType:    "client_credentials",
	}

	credentials, err := fetchAccessToken(config.AuthorizationServerUrl, payload)
	if err != nil {
		return nil, err
	}

	return &OAuthCredentialsProvider{Credentials: credentials}, nil
}

func applyEnvironmentOverrides(config *OAuthProviderConfig) {
	if envClientId := os.Getenv("ZEEBE_CLIENT_ID"); envClientId != "" {
		config.ClientId = envClientId
	}
	if envClientSecret := os.Getenv("ZEEBE_CLIENT_SECRET"); envClientSecret != "" {
		config.ClientSecret = envClientSecret
	}
	if envAudience := os.Getenv("ZEEBE_TOKEN_AUDIENCE"); envAudience != "" {
		config.Audience = envAudience
	}
	if envAuthzServerUrl := os.Getenv("ZEEBE_AUTHORIZATION_SERVER_URL"); envAuthzServerUrl != "" {
		config.AuthorizationServerUrl = envAuthzServerUrl
	}
}

func fetchAccessToken(authorizationServerUrl string, payload *oauthRequestPayload) (*OauthCredentials, error) {
	jsonPayload, err := json.Marshal(payload)
	if err != nil {
		return nil, err
	}

	reader := bytes.NewReader(jsonPayload)
	response, err := http.Post(authorizationServerUrl, "application/json", reader)
	if err != nil {
		return nil, errors.Wrap(err, fmt.Sprintf("failed while requesting access token from URL '%s'", authorizationServerUrl))
	}

	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		return nil, errors.New(fmt.Sprintf("access token request failed with status code %d and message %s", response.StatusCode, response.Status))
	}

	jsonResponse, err := ioutil.ReadAll(response.Body)
	if err != nil {
		return nil, errors.Wrap(err, "failed while reading response to access token request")
	}

	responsePayload := &OauthCredentials{}
	if err := json.Unmarshal(jsonResponse, responsePayload); err != nil {
		return nil, errors.Wrap(err, "failed while unmarshalling access token response from JSON")
	}

	return responsePayload, nil
}
