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

package cmd

import (
	"fmt"
	"github.com/zeebe-io/zeebe/clients/zbctl/utils"
	"os"

	"github.com/spf13/cobra"
)

var brokerAddr string
var cfgFile string

var out *utils.OutputWriter
var defaultErrCtx *utils.ErrorContext

var rootCmd = &cobra.Command{
	Use:   "zbctl",
	Short: "zeebe command line interface",
	Long: `zbctl is command line interface designed to create and read resources inside zeebe broker. 
It is designed for regular maintenance jobs such as:
	* deploying work flows,
	* creating jobs and workflow instances
	* consuming jobs, completing or failing jobs
	* view cluster status`,
}

// Execute adds all child commands to the root command and sets flags appropriately.
// This is called by main.main(). It only needs to happen once to the rootCmd.
func Execute() {
	if err := rootCmd.Execute(); err != nil {
		fmt.Println(err)
		os.Exit(utils.ExitCodeCommandNotFound)
	}
}

func init() {
	out = utils.NewOutputWriter()
	defaultErrCtx = new(utils.ErrorContext)
}

// initBroker will set broker address with the following precedence: flag, environment variable, default address
func initBroker(cmd *cobra.Command) {
	if cmd.Flag("broker") != nil {
		brokerAddr = cmd.Flag("broker").Value.String()
		defaultErrCtx.BrokerAddr = brokerAddr
		return
	}

	brokerAddrEnv := os.Getenv("ZB_BROKER_ADDR")
	if len(brokerAddrEnv) > 0 {
		brokerAddr = brokerAddrEnv
		defaultErrCtx.BrokerAddr = brokerAddr
		return
	}

	if cmd.Flag("broker") == nil {
		brokerAddr = utils.DefaultBrokerAddress
		defaultErrCtx.BrokerAddr = brokerAddr
		return
	}
}
