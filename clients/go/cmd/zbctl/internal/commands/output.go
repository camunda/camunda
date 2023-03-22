// Copyright © 2018 Camunda Services GmbH (info@camunda.com)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//	http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package commands

import (
	"fmt"
	"github.com/spf13/cobra"
)

const humanOutput = "human"
const jsonOutput = "json"

var outputFlag string

type Printable interface {
	human() (string, error)
	json() (string, error)
}

func addOutputFlag(c *cobra.Command) {
	c.Flags().StringVarP(
		&outputFlag,
		"output",
		"o",
		humanOutput,
		"Specify output format. Default is human readable. Possible Values: human, json",
	)
}

func printOutput(p Printable) error {
	var output string
	var err error

	if outputFlag == humanOutput {
		output, err = p.human()
	} else if outputFlag == jsonOutput {
		output, err = p.json()

	}

	if err != nil {
		return err
	}

	fmt.Println(output)
	return nil
}
