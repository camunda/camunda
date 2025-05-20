package shutdown

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/camunda/camunda/c8run/internal/types"
)

func ShutdownProcesses(c8 types.C8Run, settings types.C8RunSettings, processInfo types.Processes) {
	timeout := 30 * time.Second
	fmt.Println("Stopping all services... timeout set to ", timeout)

	progressChars := []string{"|", "/", "-", "\\"}
	tick := time.NewTicker(100 * time.Millisecond)
	done := make(chan struct{})
	go func() {
		i := 0
		for {
			select {
			case <-tick.C:
				fmt.Printf("\rStopping all services... %s", progressChars[i%len(progressChars)])
				i++
			case <-done:
				tick.Stop()
				return
			}
		}
	}()

	go func() {
		stopCommand(c8, settings, processInfo)
		close(done)
	}()

	select {
	case <-done:
		fmt.Println()
		fmt.Println("All services have been stopped.")
	case <-time.After(timeout):
		fmt.Println()
		fmt.Println("Warning: Timeout while stopping services. Some processes may still be running.")
	}
}

func stopCommand(c8 types.C8Run, settings types.C8RunSettings, processes types.Processes) {
	if !settings.DisableElasticsearch {
		err := stopProcess(c8, processes.Elasticsearch.Pid)
		if err != nil {
			fmt.Printf("%+v\n", err)
		}
		fmt.Println("Elasticsearch is stopped.")
	}
	err := stopProcess(c8, processes.Connectors.Pid)
	if err != nil {
		fmt.Printf("%+v\n", err)
	}
	fmt.Println("Connectors is stopped.")
	err = stopProcess(c8, processes.Camunda.Pid)
	if err != nil {
		fmt.Printf("%+v\n", err)
	}
	fmt.Println("Camunda is stopped.")
}

func stopProcess(c8 types.C8Run, pidfile string) error {
	fmt.Println("Stopping process with pidfile: ", pidfile)
	if _, err := os.Stat(pidfile); err == nil {
		commandPidText, _ := os.ReadFile(pidfile)
		commandPidStripped := strings.TrimSpace(string(commandPidText))
		commandPid, err := strconv.Atoi(string(commandPidStripped))
		if err != nil {
			return fmt.Errorf("stopProcess: could not stop process %d, %w", commandPid, err)
		}

		for _, process := range c8.ProcessTree(int(commandPid)) {
			fmt.Printf("Stopping process %d\n", process.Pid)
			err = process.Kill()
			if err != nil {
				return fmt.Errorf("stopProcess: could not kill process %d, %w", commandPid, err)
			}
		}
		os.Remove(pidfile)

	}
	return nil
}
