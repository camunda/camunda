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
	"github.com/spf13/cobra"
	"math"
)

var (
	Version = "development"
	Commit  = "HEAD"
)

func VersionString() string {
	commit := Commit[0:int(math.Min(8, float64(len(Commit))))]
	return fmt.Sprintf("zbctl %s (commit: %s)", Version, commit)
}

var versionCmd = &cobra.Command{
	Use:   "version",
	Short: "Print the version of zbctl",
	Args:  cobra.NoArgs,
	Run: func(cmd *cobra.Command, args []string) {
		fmt.Println(VersionString())
	},
}

func init() {
	rootCmd.AddCommand(versionCmd)
}
