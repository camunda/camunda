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
package utils

import (
	"fmt"
	"os"
	"strings"
)

type ErrorContext struct {
	Address string
}

func errorOutput(err error, ctx *ErrorContext) string {
	if strings.Contains(err.Error(), "connection refused") {
		return fmt.Sprintf("unable to connect to broker %s", ctx.Address)
	}
	return err.Error()
}

func CheckOrExit(err error, exitCode int, ctx *ErrorContext) {
	if err == nil {
		return
	}

	fmt.Printf("%+v \n\n", errorOutput(err, ctx))
	os.Exit(exitCode)
}
