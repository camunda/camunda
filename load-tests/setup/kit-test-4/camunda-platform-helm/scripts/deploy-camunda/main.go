package main

import (
	"os"
	"scripts/deploy-camunda/cmd"
)

func main() {
	if err := cmd.Execute(); err != nil {
		os.Exit(1)
	}
}
