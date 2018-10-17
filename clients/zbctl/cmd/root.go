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
	"github.com/zeebe-io/zeebe/clients/go"
	"github.com/zeebe-io/zeebe/clients/zbctl/utils"
	"os"
	"strconv"
	"strings"

	"github.com/spf13/cobra"
)

var client zbc.ZBClient

var addressFlag string
var out *utils.OutputWriter
var defaultErrCtx *utils.ErrorContext

var rootCmd = &cobra.Command{
	Use:   "zbctl",
	Short: "zeebe command line interface",
	Long: `zbctl is command line interface designed to create and read resources inside zeebe broker. 
It is designed for regular maintenance jobs such as:
	* deploying workflows,
	* creating jobs and workflow instances
	* activating, completing or failing jobs
	* update payload and retries
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

	rootCmd.PersistentFlags().StringVar(&addressFlag, "address", "", "Specify the Zeebe addressFlag")
}

// initClient will create a client with in the following precedence: address flag, environment variable, default address
var initClient = func(cmd *cobra.Command, args []string) {
	address := utils.DefaultAddressHost

	addressEnv := os.Getenv("ZEEBE_ADDRESS")
	if len(addressEnv) > 0 {
		address = addressEnv
	}

	if len(addressFlag) > 0 {
		address = addressFlag
	}

	address = appendPort(address)

	defaultErrCtx.Address = address

	var err error
	client, err = zbc.NewZBClient(address)
	utils.CheckOrExit(err, utils.ExitCodeConfigurationError, defaultErrCtx)
}

func convertToKey(arg string, errorMsg string) int64 {
	key, err := strconv.ParseInt(arg, 10, 64)
	if err != nil {
		fmt.Println(errorMsg, arg)
		utils.CheckOrExit(err, utils.ExitCodeIOError, defaultErrCtx)
	}
	return key
}

func appendPort(address string) string {
	if strings.Contains(address, ":") {
		return address
	} else {
		return fmt.Sprintf("%s:%d", address, utils.DefaultAddressPort)
	}
}
