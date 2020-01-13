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

package main

import (
	"context"
	"fmt"
	"io/ioutil"
	"os"
	"os/exec"
	"regexp"
	"runtime"
	"strings"
	"testing"
	"time"

	"github.com/google/go-cmp/cmp"
	"github.com/stretchr/testify/suite"
	"github.com/zeebe-io/zeebe/clients/go/internal/containersuite"
	"github.com/zeebe-io/zeebe/clients/go/pkg/zbc"
)

var zbctl string

type integrationTestSuite struct {
	*containersuite.ContainerSuite
}

var tests = []struct {
	name       string
	setupCmds  []string
	envVars    []string
	cmd        string
	goldenFile string
}{
	{
		name:       "print help",
		cmd:        "help",
		envVars:    []string{"HOME=/tmp"},
		goldenFile: "testdata/help.golden",
	},
	{
		name:       "print version",
		cmd:        "version",
		envVars:    []string{"HOME=/tmp"},
		goldenFile: "testdata/version.golden",
	},
	{
		name:       "missing insecure flag",
		cmd:        "status",
		envVars:    []string{"HOME=/tmp"},
		goldenFile: "testdata/without_insecure.golden",
	},
	{
		name: "using insecure env var",
		cmd:  "status",
		// we need to set the path so it evaluates $HOME before we overwrite it
		envVars:    []string{fmt.Sprintf("%s=true", zbc.InsecureEnvVar), fmt.Sprintf("PATH=%s", os.Getenv("PATH"))},
		goldenFile: "testdata/topology.golden",
	},
	{
		name:       "deploy workflow",
		cmd:        "--insecure deploy testdata/model.bpmn",
		goldenFile: "testdata/deploy.golden",
	},
	{
		name:       "create instance",
		setupCmds:  []string{"--insecure deploy testdata/model.bpmn"},
		cmd:        "--insecure create instance process",
		goldenFile: "testdata/create_instance.golden",
	},
	{
		name:       "create worker",
		setupCmds:  []string{"--insecure deploy testdata/job_model.bpmn", "--insecure create instance jobProcess"},
		cmd:        "create --insecure worker jobType --handler echo",
		goldenFile: "testdata/create_worker.golden",
	},
	{
		name:       "activate job",
		setupCmds:  []string{"--insecure deploy testdata/job_model.bpmn", "--insecure create instance jobProcess"},
		cmd:        "--insecure activate jobs jobType",
		goldenFile: "testdata/activate_job.golden",
	},
}

func TestZbctlWithInsecureGateway(t *testing.T) {
	err := buildZbctl()
	if err != nil {
		t.Fatal(fmt.Errorf("couldn't build zbctl: %w", err))
	}

	suite.Run(t,
		&integrationTestSuite{
			ContainerSuite: &containersuite.ContainerSuite{
				WaitTime:       time.Second,
				ContainerImage: "camunda/zeebe:current-test",
			},
		})
}

func (s *integrationTestSuite) TestCommonCommands() {
	for _, test := range tests {
		s.T().Run(test.name, func(t *testing.T) {
			for _, cmd := range test.setupCmds {
				if _, err := s.runCommand(cmd); err != nil {
					t.Fatal(fmt.Errorf("failed while executing set up command '%s': %w", cmd, err))
				}
			}

			cmdOut, _ := s.runCommand(test.cmd, test.envVars...)
			goldenOut, err := ioutil.ReadFile(test.goldenFile)
			if err != nil {
				t.Fatal(err)
			}
			want := strings.Split(string(goldenOut), "\n")
			got := strings.Split(string(cmdOut), "\n")

			if diff := cmp.Diff(want, got, cmp.Comparer(compareStrIgnoreNumbers)); diff != "" {
				t.Fatalf("%s: diff (-want +got):\n%s", test.name, diff)
			}
		})
	}
}

func compareStrIgnoreNumbers(x, y string) bool {
	reg := regexp.MustCompile(`\d`)

	newX := reg.ReplaceAllString(x, "")
	newY := reg.ReplaceAllString(y, "")

	return newX == newY
}

// runCommand runs the zbctl command and returns the combined output from stdout and stderr
func (s *integrationTestSuite) runCommand(command string, envVars ...string) ([]byte, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	args := append(strings.Fields(command), "--address", s.GatewayAddress)
	cmd := exec.CommandContext(ctx, fmt.Sprintf("./dist/%s", zbctl), args...)

	cmd.Env = append(cmd.Env, envVars...)
	return cmd.CombinedOutput()
}

func buildZbctl() error {
	if runtime.GOOS == "linux" {
		zbctl = "zbctl"
	} else {
		return fmt.Errorf("can't run zbctl tests on unsupported OS '%s'", runtime.GOOS)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	cmd := exec.CommandContext(ctx, "./build.sh", runtime.GOOS)
	cmd.Env = append(cmd.Env, "HOME=/tmp", "RELEASE_VERSION=release-test", "RELEASE_HASH=1234567890")
	return cmd.Run()
}
