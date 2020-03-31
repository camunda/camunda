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
	"github.com/stretchr/testify/require"
	"testing"
)

func TestReadEnvWrapper(t *testing.T) {
	// given
	env.set(CaCertificatePath, "path")
	defer env.unset(CaCertificatePath)

	// when
	config := &ClientConfig{}
	_, _ = NewClient(config)

	// then
	require.EqualValues(t, "path", config.CaCertificatePath)
}

func TestUnsetEnv(t *testing.T) {
	// given
	env.set(CaCertificatePath, "path")
	env.unset(CaCertificatePath)

	// when
	config := &ClientConfig{}
	_, _ = NewClient(config)

	// then
	require.Empty(t, config.CaCertificatePath)

}

func TestRestoreEnv(t *testing.T) {
	// given
	presentVar := "present_env_var"
	absentVar := "absent_env_var"

	env.set(presentVar, "someValue")
	defer env.unset(presentVar)
	environ := env.copy()
	env.set(absentVar, "someValue")

	// when
	env.overwrite(environ)

	// then
	_, present := env.lookup(presentVar)
	require.True(t, present)

	_, present = env.lookup(absentVar)
	require.False(t, present)
}
