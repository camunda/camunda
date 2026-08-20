package jre

import (
	"path/filepath"
	"runtime"
)

const DirectoryName = "jre"

func Home(parentDir string) string {
	return filepath.Join(parentDir, DirectoryName)
}

func JavaBinary(parentDir string) string {
	javaBinary := "java"
	if runtime.GOOS == "windows" {
		javaBinary = "java.exe"
	}
	return filepath.Join(Home(parentDir), "bin", javaBinary)
}
