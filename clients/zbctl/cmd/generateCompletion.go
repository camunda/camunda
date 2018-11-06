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
	"errors"
	"fmt"
	"github.com/spf13/cobra"
	"os"
)

var generateCompletionShellFlag string

var generateCompletionCmd = &cobra.Command{
	Use:   "completion",
	Short: "Generate shell completion for zbctl",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		var err error
		switch generateCompletionShellFlag {
		case "bash":
			rootCmd.GenBashCompletion(os.Stdout)
			break
		case "zsh":
			rootCmd.GenZshCompletion(os.Stdout)
			break
		default:
			err = errors.New(fmt.Sprint("Generating completion for shell", generateCompletionShellFlag, "not supported"))
		}
		return err
	},
}

func init() {
	generateCmd.AddCommand(generateCompletionCmd)
	generateCompletionCmd.Flags().StringVar(&generateCompletionShellFlag, "shell", "bash", "Specify shell to generate completion")
}
