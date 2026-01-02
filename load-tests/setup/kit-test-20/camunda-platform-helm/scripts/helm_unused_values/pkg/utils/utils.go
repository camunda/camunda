package utils

import (
	"fmt"
	"os"
	"os/exec"
)

func CheckDependencies() (bool, []string) {
	var missing []string

	if _, err := exec.LookPath("yq"); err != nil {
		missing = append(missing, "yq")
	}

	if _, err := exec.LookPath("jq"); err != nil {
		missing = append(missing, "jq")
	}

	return len(missing) == 0, missing
}

func DetectRipgrep() bool {
	_, err := exec.LookPath("rg")
	return err == nil
}

func ValidateFile(file string) error {
	info, err := os.Stat(file)
	if err != nil {
		return fmt.Errorf("file %s not found: %w", file, err)
	}
	if info.IsDir() {
		return fmt.Errorf("%s is a directory, not a file", file)
	}
	return nil
}

func ValidateDirectory(dir string) error {
	info, err := os.Stat(dir)
	if err != nil {
		return fmt.Errorf("directory %s not found: %w", dir, err)
	}
	if !info.IsDir() {
		return fmt.Errorf("%s is not a directory", dir)
	}
	return nil
}
