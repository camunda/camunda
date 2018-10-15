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

var completionShellFlag string

// completionCmd represents the completion command
var completionCmd = &cobra.Command{
	Use:   "completion",
	Short: "Generate shell completion for zbctl",
	Args: cobra.NoArgs,
	Run: func(cmd *cobra.Command, args []string) {
		fmt.Println("completion called")
		switch completionShellFlag {
		case "bash":
			rootCmd.GenBashCompletion(os.Stdout)
			break
		case "zsh":
			rootCmd.GenZshCompletion(os.Stdout)
			break
		default:
			fmt.Println("Unsupported shell for completion generation", completionShellFlag)
			os.Exit(utils.ExitCodeConfigurationError)
		}
	},
}

func init() {
	rootCmd.AddCommand(completionCmd)
    completionCmd.Flags().StringVar(&completionShellFlag, "shell", "bash", "Shell to generate completion for")
}
