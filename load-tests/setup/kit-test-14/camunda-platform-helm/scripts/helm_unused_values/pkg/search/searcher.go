package search

import (
	"fmt"
	"os/exec"
	"strings"
)

func (f *Finder) searchFiles(pattern, directory string) []string {
	f.Display.DebugLog(fmt.Sprintln("Search Request:"))
	f.Display.DebugLog(fmt.Sprintln("  Pattern:    ", pattern))
	f.Display.DebugLog(fmt.Sprintln("  Directory:  ", directory))
	f.Display.DebugLog(fmt.Sprintln("  Using:      ", map[bool]string{true: "ripgrep", false: "grep"}[f.UseRipgrep]))

	matches := f.executeSearchCommand(pattern, directory)

	if len(matches) > 0 {
		f.Display.DebugLog(fmt.Sprintf("✓ Search complete: Found %d matches\n", len(matches)))
	} else {
		f.Display.DebugLog(fmt.Sprintln("✗ Search complete: No matches found"))
	}

	return matches
}

// executeSearchCommand performs the actual command execution
func (f *Finder) executeSearchCommand(pattern, directory string) []string {
	var cmd *exec.Cmd
	var output []byte
	var err error

	var shellCmd string
	if f.UseRipgrep {
		shellCmd = fmt.Sprintf("rg --no-heading --with-filename --line-number -- %s %s",
			pattern, directory)
	} else {
		shellCmd = fmt.Sprintf("grep -r -n -F %s %s",
			pattern, directory)
	}

	f.Display.DebugLog(fmt.Sprintln("\nCommand Details:"))
	f.Display.DebugLog(fmt.Sprintln("  Shell command: ", shellCmd))

	cmd = exec.Command("sh", "-c", shellCmd)

	output, err = cmd.Output()
	if err != nil {
		// Exit code 1 means no matches found, which is expected
		if exitErr, ok := err.(*exec.ExitError); ok && exitErr.ExitCode() == 1 {
			f.Display.DebugLog(fmt.Sprintln("→ No matches found (exit code 1)"))
			return []string{}
		}
		f.Display.DebugLog(fmt.Sprintf("✗ Error running search command: %v\n", err))
		if exitErr, ok := err.(*exec.ExitError); ok {
			f.Display.DebugLog(fmt.Sprintf("  Exit code: %d\n", exitErr.ExitCode()))
			f.Display.DebugLog(fmt.Sprintf("  Stderr: %s\n", string(exitErr.Stderr)))
		}
		return []string{}
	}

	matches := strings.Split(strings.TrimSpace(string(output)), "\n")
	if len(matches) == 1 && matches[0] == "" {
		f.Display.DebugLog(fmt.Sprintln("→ Empty output, no matches"))
		return []string{}
	}

	f.Display.DebugLog(fmt.Sprintf("✓ Found %d matches\n", len(matches)))

	return matches
}
