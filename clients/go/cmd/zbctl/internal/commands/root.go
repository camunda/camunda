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

package commands

import (
	"fmt"
	"github.com/camunda-cloud/zeebe/clients/go/pkg/zbc"
	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"
	"net"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/spf13/cobra"
)

const defaultTimeout = 10 * time.Second

var client zbc.Client

var addressFlag string
var hostFlag string
var portFlag string
var caCertPathFlag string
var clientIDFlag string
var clientSecretFlag string
var audienceFlag string
var authzURLFlag string
var insecureFlag bool
var clientCacheFlag string

var rootCmd = &cobra.Command{
	Use:   "zbctl",
	Short: "zeebe command line interface",
	Long: `zbctl is a command line interface designed to create and read resources inside zeebe broker.
It is designed for regular maintenance jobs such as:
	* deploying processes,
	* creating jobs and process instances
	* activating, completing or failing jobs
	* update variables and retries
	* view cluster status`,
	PersistentPreRun: func(cmd *cobra.Command, args []string) {
		// silence help here instead of as a parameter because we only want to suppress it on a 'Zeebe' error and not if
		// parsing args fails
		cmd.SilenceUsage = true
	},
	PersistentPostRunE: func(cmd *cobra.Command, args []string) error {
		if client != nil {
			return client.Close()
		}

		return nil
	},
}

// Execute adds all child commands to the root command and sets flags appropriately.
// This is called by main.main(). It only needs to happen once to the rootCmd.
func Execute() {
	if err := rootCmd.Execute(); err != nil {
		os.Exit(1)
	}
}

func init() {
	rootCmd.PersistentFlags().StringVar(&hostFlag, "host", "", fmt.Sprintf("Specify the host part of the gateway address. If omitted, will read from the environment variable '%s' (default '%s')", zbc.GatewayHostEnvVar, zbc.DefaultAddressHost))
	rootCmd.PersistentFlags().StringVar(&portFlag, "port", "", fmt.Sprintf("Specify the port part of the gateway address. If omitted, will read from the environment variable '%s' (default '%s')", zbc.GatewayPortEnvVar, zbc.DefaultAddressPort))
	rootCmd.PersistentFlags().StringVar(&addressFlag, "address", "", "Specify a contact point address. If omitted, will read from the environment variable '"+zbc.GatewayAddressEnvVar+"' (default '"+fmt.Sprintf("%s:%s", zbc.DefaultAddressHost, zbc.DefaultAddressPort)+"')")
	rootCmd.PersistentFlags().StringVar(&caCertPathFlag, "certPath", "", "Specify a path to a certificate with which to validate gateway requests. If omitted, will read from the environment variable '"+zbc.CaCertificatePath+"'")
	rootCmd.PersistentFlags().StringVar(&clientIDFlag, "clientId", "", "Specify a client identifier to request an access token. If omitted, will read from the environment variable '"+zbc.OAuthClientIdEnvVar+"'")
	rootCmd.PersistentFlags().StringVar(&clientSecretFlag, "clientSecret", "", "Specify a client secret to request an access token. If omitted, will read from the environment variable '"+zbc.OAuthClientSecretEnvVar+"'")
	rootCmd.PersistentFlags().StringVar(&audienceFlag, "audience", "", "Specify the resource that the access token should be valid for. If omitted, will read from the environment variable '"+zbc.OAuthTokenAudienceEnvVar+"'")
	rootCmd.PersistentFlags().StringVar(&authzURLFlag, "authzUrl", zbc.OAuthDefaultAuthzURL, "Specify an authorization server URL from which to request an access token. If omitted, will read from the environment variable '"+zbc.OAuthAuthorizationUrlEnvVar+"'")
	rootCmd.PersistentFlags().BoolVar(&insecureFlag, "insecure", false, "Specify if zbctl should use an unsecured connection. If omitted, will read from the environment variable '"+zbc.InsecureEnvVar+"'")
	rootCmd.PersistentFlags().StringVar(&clientCacheFlag, "clientCache", zbc.DefaultOauthYamlCachePath, "Specify the path to use for the OAuth credentials cache. If omitted, will read from the environment variable '"+zbc.OAuthCachePathEnvVar+"'")
}

// initClient will create a client with in the following precedence: flag, environment variable, default
var initClient = func(cmd *cobra.Command, args []string) error {
	var err error
	var credsProvider zbc.CredentialsProvider

	host, port, err := parseAddress()
	if err != nil {
		return err
	}

	// override env vars with CLI parameters, if any
	if err := setSecurityParamsAsEnv(); err != nil {
		return err
	}

	_, idExists := os.LookupEnv(zbc.OAuthClientIdEnvVar)
	_, secretExists := os.LookupEnv(zbc.OAuthClientSecretEnvVar)

	if idExists || secretExists {
		_, audienceExists := os.LookupEnv(zbc.OAuthTokenAudienceEnvVar)
		if !audienceExists {
			if err := os.Setenv(zbc.OAuthTokenAudienceEnvVar, host); err != nil {
				return err
			}
		}

		providerConfig := zbc.OAuthProviderConfig{}

		// create a credentials provider with the specified parameters
		credsProvider, err = zbc.NewOAuthCredentialsProvider(&providerConfig)

		if err != nil {
			return err
		}
	}

	client, err = zbc.NewClient(&zbc.ClientConfig{
		GatewayAddress:      fmt.Sprintf("%s:%s", host, port),
		CredentialsProvider: credsProvider,
	})
	return err
}

func setSecurityParamsAsEnv() (err error) {
	setEnv := func(envVar, value string) {
		if err == nil {
			err = os.Setenv(envVar, value)
		}
	}

	if insecureFlag {
		setEnv(zbc.InsecureEnvVar, "true")
	}
	if caCertPathFlag != "" {
		setEnv(zbc.CaCertificatePath, caCertPathFlag)
	}
	if clientIDFlag != "" {
		setEnv(zbc.OAuthClientIdEnvVar, clientIDFlag)
	}
	if clientSecretFlag != "" {
		setEnv(zbc.OAuthClientSecretEnvVar, clientSecretFlag)
	}
	if audienceFlag != "" {
		setEnv(zbc.OAuthTokenAudienceEnvVar, audienceFlag)
	}
	if shouldOverwriteEnvVar("authzUrl", zbc.OAuthAuthorizationUrlEnvVar) {
		setEnv(zbc.OAuthAuthorizationUrlEnvVar, authzURLFlag)
	}
	if shouldOverwriteEnvVar("clientCache", zbc.DefaultOauthYamlCachePath) {
		setEnv(zbc.OAuthCachePathEnvVar, clientCacheFlag)
	}

	return
}

// decides whether to overwrite env var (for parameters with default values)
func shouldOverwriteEnvVar(cliParam, envVar string) bool {
	cliParameterSet := rootCmd.Flags().Changed(cliParam)
	_, exists := os.LookupEnv(envVar)
	return cliParameterSet || !exists
}

func parseAddress() (host string, port string, err error) {

	if shouldUseAddress() {
		return getHostPortFromAddress()
	}
	host, port = getHostPort()
	return host, port, nil
}

func parseHostAndPortFromAddress(parsedAddress string) (string, string, error) {
	host, port, err := net.SplitHostPort(parsedAddress)
	if err != nil && strings.Contains(err.Error(), "missing port in address") {
		host = parsedAddress
		port = zbc.DefaultAddressPort
		err = nil
	}
	return host, port, err
}

func shouldUseAddress() bool {
	_, hostEnvExists := os.LookupEnv(zbc.GatewayHostEnvVar)
	_, portEnvExists := os.LookupEnv(zbc.GatewayPortEnvVar)
	return len(hostFlag) == 0 && len(portFlag) == 0 && !portEnvExists && !hostEnvExists
}

func getHostPortFromAddress() (host string, port string, err error) {
	host = zbc.DefaultAddressHost
	port = zbc.DefaultAddressPort

	if len(addressFlag) > 0 {
		host, port, err = parseHostAndPortFromAddress(addressFlag)
	} else {
		addressEnv, addressEnvExists := os.LookupEnv(zbc.GatewayAddressEnvVar)
		if addressEnvExists {
			host, port, err = parseHostAndPortFromAddress(addressEnv)
		}
	}
	return host, port, err
}

func getHostPort() (host string, port string) {
	host = zbc.DefaultAddressHost
	port = zbc.DefaultAddressPort

	lenOfHostFlag := len(hostFlag)
	lenOfPortFlag := len(portFlag)
	hostEnv, hostEnvExists := os.LookupEnv(zbc.GatewayHostEnvVar)
	portEnv, portEnvExists := os.LookupEnv(zbc.GatewayPortEnvVar)

	if lenOfHostFlag > 0 {
		host = hostFlag
	} else if hostEnvExists {
		host = hostEnv
	}

	if lenOfPortFlag > 0 {
		port = portFlag
	} else if portEnvExists {
		port = portEnv
	}

	return host, port
}

func keyArg(key *int64) cobra.PositionalArgs {
	return func(cmd *cobra.Command, args []string) error {
		if len(args) != 1 {
			return fmt.Errorf("expects key as only positional argument")
		}

		value, err := strconv.ParseInt(args[0], 10, 64)
		if err != nil {
			return fmt.Errorf("invalid argument %q for %q: %s", args[0], "key", err)
		}

		*key = value

		return nil
	}
}

func printJSON(value proto.Message) error {
	json, err := toJSON(value)
	if err == nil {
		fmt.Println(json)
	}
	return err
}

func toJSON(value proto.Message) (string, error) {
	m := protojson.MarshalOptions{EmitUnpopulated: true, Indent: "  "}
	valueJSON, err := m.Marshal(value)
	if err == nil {
		return string(valueJSON), nil
	}
	return "", err
}
