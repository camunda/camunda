// Copyright 2022 Camunda Services GmbH
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package utils

import (
	"camunda-platform/test/unit/testhelpers"
	"flag"
	"os"
	"regexp"
	"testing"

	"github.com/stretchr/testify/suite"
)

var update = flag.Bool("update-golden", false, "update golden test output files")

type TemplateGoldenTest struct {
	suite.Suite
	ChartPath      string
	Release        string
	Namespace      string
	GoldenFileName string
	Templates      []string
	IgnoredLines   []string
	SetValues      map[string]string
	ExtraHelmArgs  []string
}

func (s *TemplateGoldenTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name:                    "TestContainerGoldenTestDefaults",
			Values:                  s.SetValues,
			RenderTemplateExtraArgs: s.ExtraHelmArgs,
			Verifier: func(t *testing.T, output string, err error) {
				s.IgnoredLines = append(s.IgnoredLines, `\s+helm.sh/chart:\s+.*`)
				bytes := []byte(output)
				for _, ignoredLine := range s.IgnoredLines {
					regex := regexp.MustCompile(ignoredLine)
					bytes = regex.ReplaceAll(bytes, []byte(""))
				}
				output = string(bytes)

				goldenFile := "golden/" + s.GoldenFileName + ".golden.yaml"

				if *update {
					err := os.WriteFile(goldenFile, bytes, 0644)
					s.Require().NoError(err, "Golden file was not writable")
				}

				expected, e := os.ReadFile(goldenFile)

				// then
				s.Require().NoError(e, "Golden file doesn't exist or was not readable")
				s.Require().Equal(string(expected), output)
			},
		},
	}
	testhelpers.RunTestCasesE(s.T(), s.ChartPath, s.Release, s.Namespace, s.Templates, testCases)
}
