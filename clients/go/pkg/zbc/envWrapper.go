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
	"flag"
	"github.com/stretchr/testify/suite"
	"os"
)

var env = &envWrapper{vars: make(map[string]string)}

func notInTest() bool {
	return flag.Lookup("test.v") == nil
}

type envWrapper struct {
	vars map[string]string
}

func (w *envWrapper) set(variable, value string) {
	w.vars[variable] = value
}

func (w *envWrapper) get(variable string) string {
	value := w.vars[variable]
	if value == "" && notInTest() {
		value = os.Getenv(variable)
	}

	return value
}

func (w *envWrapper) unset(variable string) {
	delete(w.vars, variable)
}

func (w *envWrapper) lookup(variable string) (string, bool) {
	value, ok := w.vars[variable]
	if !ok && notInTest() {
		value, ok = os.LookupEnv(variable)
	}

	return value, ok
}

func (w *envWrapper) copy() map[string]string {
	envDup := make(map[string]string, len(w.vars))

	for envVar, val := range w.vars {
		envDup[envVar] = val
	}

	return envDup
}

func (w *envWrapper) overwrite(environ map[string]string) {
	w.vars = environ
}

type envSuite struct {
	suite.Suite
	envCopy map[string]string
}

func (s *envSuite) SetupTest() {
	s.envCopy = env.copy()
}

func (s *envSuite) TearDownTest() {
	env.overwrite(s.envCopy)
}
