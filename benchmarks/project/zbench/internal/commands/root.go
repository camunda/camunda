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
	grpc_prometheus "github.com/grpc-ecosystem/go-grpc-prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"github.com/spf13/cobra"
	"github.com/zeebe-io/zeebe/clients/go/pkg/zbc"
	"google.golang.org/grpc"
	"log"
	"net/http"
	"os"
	"strings"
	"time"
)

const (
	DefaultAddressHost    = "127.0.0.1"
	DefaultAddressPort    = "26500"
	AddressEnvVar         = "ZEEBE_ADDRESS"
	DefaultTimeout        = 10 * time.Second
	DefaultMonitoringHost = "127.0.0.1"
	DefaultMonitoringPort = 9600
)

var (
	client             zbc.Client
	addressFlag        string
	caCertPathFlag     string
	clientIDFlag       string
	clientSecretFlag   string
	audienceFlag       string
	authzURLFlag       string
	clientCacheFlag    string
	monitoringHostFlag string
	insecureFlag       bool
	monitoringPortFlag int
)

var rootCmd = &cobra.Command{
	Use:   "zbench",
	Short: "zeebe benchmark application",
	Long:  `zbench is command line interface designed to start a Zeebe workflow at a regular interval, and complete them`,
	PersistentPreRun: func(cmd *cobra.Command, args []string) {
		// silence help here instead of as a parameter because we only want to suppress it on a 'Zeebe' error and not if
		// parsing args fails
		cmd.SilenceUsage = true

		// start monitoring server
		http.Handle("/metrics", promhttp.Handler())
		go func() {
			err := http.ListenAndServe(fmt.Sprintf("%s:%d", monitoringHostFlag, monitoringPortFlag), nil)
			if err != nil {
				log.Printf("Failed to start monitoring server: %s", err)
			}
		}()
		log.Println("Started")
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
	rootCmd.PersistentFlags().StringVar(&addressFlag, "address", "", "Specify a contact point address. If omitted, will read from the environment variable '"+AddressEnvVar+"' (default '"+fmt.Sprintf("%s:%s", DefaultAddressHost, DefaultAddressPort)+"')")
	rootCmd.PersistentFlags().StringVar(&caCertPathFlag, "certPath", "", "Specify a path to a certificate with which to validate gateway requests. If omitted, will read from the environment variable '"+zbc.CaCertificatePath+"'")
	rootCmd.PersistentFlags().StringVar(&clientIDFlag, "clientId", "", "Specify a client identifier to request an access token. If omitted, will read from the environment variable '"+zbc.OAuthClientIdEnvVar+"'")
	rootCmd.PersistentFlags().StringVar(&clientSecretFlag, "clientSecret", "", "Specify a client secret to request an access token. If omitted, will read from the environment variable '"+zbc.OAuthClientSecretEnvVar+"'")
	rootCmd.PersistentFlags().StringVar(&audienceFlag, "audience", "", "Specify the resource that the access token should be valid for. If omitted, will read from the environment variable '"+zbc.OAuthTokenAudienceEnvVar+"'")
	rootCmd.PersistentFlags().StringVar(&authzURLFlag, "authzUrl", zbc.OAuthDefaultAuthzURL, "Specify an authorization server URL from which to request an access token. If omitted, will read from the environment variable '"+zbc.OAuthAuthorizationUrlEnvVar+"'")
	rootCmd.PersistentFlags().BoolVar(&insecureFlag, "insecure", false, "Specify if zbctl should use an unsecured connection. If omitted, will read from the environment variable '"+zbc.InsecureEnvVar+"'")
	rootCmd.PersistentFlags().StringVar(&clientCacheFlag, "clientCache", zbc.DefaultOauthYamlCachePath, "Specify the path to use for the OAuth credentials cache. If omitted, will read from the environment variable '"+zbc.OAuthCachePathEnvVar+"'")
	rootCmd.PersistentFlags().StringVar(&monitoringHostFlag, "monitoringHost", DefaultMonitoringHost, "Specify the host for the monitoring server")
	rootCmd.PersistentFlags().IntVar(&monitoringPortFlag, "monitoringPort", DefaultMonitoringPort, "Specify the port for the monitoring server")
}

// initClient will create a client with in the following precedence: flag, environment variable, default
var initClient = func(cmd *cobra.Command, args []string) error {
	var err error
	var credsProvider zbc.CredentialsProvider

	host, port := parseAddress()

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
		DialOpts: []grpc.DialOption{
			grpc.WithUnaryInterceptor(grpc_prometheus.UnaryClientInterceptor),
			grpc.WithStreamInterceptor(grpc_prometheus.StreamClientInterceptor),
		},
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

func parseAddress() (address string, port string) {
	address = DefaultAddressHost
	port = DefaultAddressPort

	if len(addressFlag) > 0 {
		address = addressFlag
	} else if addressEnv, exists := os.LookupEnv(AddressEnvVar); exists {
		address = addressEnv
	}

	if strings.Contains(address, ":") {
		parts := strings.Split(address, ":")
		address = parts[0]
		port = parts[1]
	}

	return
}
