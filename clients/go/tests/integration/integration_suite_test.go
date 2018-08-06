package integration_test

import (
	"testing"

	"log"
	"os"
	"os/exec"
	"time"

	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
)

var broker *exec.Cmd = nil

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
	time.Sleep(time.Duration(time.Second * 5))
}

func stopBroker() {
	if broker != nil {
		broker.Process.Kill()
	}
	time.Sleep(time.Duration(time.Second * 1))
}

func TestIntegration(t *testing.T) {
	RegisterFailHandler(Fail)
	RunSpecs(t, "Integration Suite")
}
