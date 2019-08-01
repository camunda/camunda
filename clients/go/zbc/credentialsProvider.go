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
	"fmt"
	"io/ioutil"
	"log"
	"os"
	"strings"

	"gopkg.in/yaml.v2"
)

// CredentialsProvider is responsible for adding credentials to each gRPC call's headers.
type CredentialsProvider interface {
	// Takes a map of gRPC headers as defined in credentials.PerRPCCredentials and adds credentials to them
	ApplyCredentials(headers map[string]string)
}

// A built-in CredentialsProvider that expects a path to a Zeebe credentials YAML file containing a prefix and an
// access token. Using these values it sets the 'Authorization' header of each gRPC call.
//
// The (current) specification for the Zeebe credentials file is as follows:
//	endpoint:
//		auth:
//			credentials:
//				access_token: <token>,
//				token_type: <prefix>,
type ZeebeClientCredentialsProvider struct {
	zeebeCredentialsPath string
}

type zeebeClientCredentials struct {
	Endpoint struct {
		Auth struct {
			Credentials struct {
				AccessToken string `yaml:"access_token"`
				ExpiresIn   string `yaml:"expires_in"`
				TokenType   string `yaml:"token_type"`
			}
		}
	}
}

// Takes a map of headers as input and adds an access token prefixed by a token type to the 'Authorization'
// header of a gRPC call. The access token and the token type are obtained from a Zeebe credentials YAML file that
// should be found at the provided zeebeCredentialsPath.
//
// To use this with a JSON Web Token (JWT), the Zeebe credentials file would look like:
//	endpoint:
//		auth:
//			credentials:
//				access_token: jjjjj.wwwww.ttttt,
//				token_type: Bearer,
func (provider *ZeebeClientCredentialsProvider) ApplyCredentials(headers map[string]string) {
	file, err := os.Open(provider.zeebeCredentialsPath)
	if err != nil {
		log.Fatalf("Failed to open the zeebe credentials file: %s", err)
	}

	bytes, err := ioutil.ReadAll(file)
	if err != nil {
		log.Fatalf("Failed to read the zeebe credentials file: %s", err)

	}

	zbCreds := &zeebeClientCredentials{}

	if err = yaml.Unmarshal(bytes, zbCreds); err != nil {
		log.Fatalf("Failed to unmarshal the zeebe credentials file from YAML: %s", err)
	}

	tokenType := strings.TrimSpace(zbCreds.Endpoint.Auth.Credentials.TokenType)
	token := strings.TrimSpace(zbCreds.Endpoint.Auth.Credentials.AccessToken)

	headers["Authorization"] = fmt.Sprintf("%s %s", tokenType, token)
}

// Creates a ZeebeClientCredentialsProvider with a path to the Zeebe credentials YAML file.
func NewZeebeClientCredentialsProvider(zeebeCredentialsPath string) (*ZeebeClientCredentialsProvider, error) {
	if zeebeCredentialsPath == "" {
		return nil, newEmptyPathError("Zeebe credentials file")
	}

	if _, err := os.Stat(zeebeCredentialsPath); err != nil {
		return nil, newNoSuchFileError("Zeebe credentials file", zeebeCredentialsPath)

	}

	return &ZeebeClientCredentialsProvider{zeebeCredentialsPath: zeebeCredentialsPath}, nil
}
