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

package integration_test

import (
	"strings"
	"testing"

	"log"
	"os"
	"os/exec"
	"time"

	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
)

var broker *exec.Cmd = nil

func Contains(a string, list []string) bool {
	for _, b := range list {
		if strings.Compare(a, b) == 0 {
			return true
		}
	}
	return false
}

func startBroker() {
	cmdPath := "../../../../dist/target/zeebe-broker/bin/broker"
	if _, err := os.Stat(cmdPath); os.IsNotExist(err) {
		log.Fatalf("Zeebe broker startup script '%v' not found, run maven build in dist folder first\n", cmdPath)
	}

	cmd := exec.Command(cmdPath)
	cmd.Stdout = os.Stdout
	if err := cmd.Start(); err != nil {
		log.Println("Error:", err)
	}

	broker = cmd
	log.Println("broker pid: ", broker.Process.Pid)
	log.Println("warming up")
	time.Sleep(time.Duration(time.Second * 10))
}

func stopBroker() {
	if broker != nil {
		broker.Process.Kill()
	}
	time.Sleep(time.Duration(time.Second * 1))
	dataDirPath := "../../../../dist/target/zeebe-broker/data"
	err := os.RemoveAll(dataDirPath)
	if err != nil {
		log.Fatal(err)
	}
}

func TestIntegration(t *testing.T) {

	BeforeSuite(func() {
		log.Print("starting broker")
		startBroker()
	})

	AfterSuite(func() {
		log.Println("killing broker")
		stopBroker()
	})

	RegisterFailHandler(Fail)
	RunSpecs(t, "Integration Suite")
}
