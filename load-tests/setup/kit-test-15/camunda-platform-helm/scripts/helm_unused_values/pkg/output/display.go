package output

import (
	"fmt"

	"github.com/fatih/color"
)

// Display handles terminal output and progress display
type Display struct {
	NoColors  bool
	QuietMode bool
	Debug     bool
}

// NewDisplay creates a new display handler
func NewDisplay(noColors, quietMode bool, debug bool) *Display {
	if noColors {
		color.NoColor = true
	}

	return &Display{
		NoColors:  noColors,
		QuietMode: quietMode,
		Debug:     debug,
	}
}

// PrintInfo prints an informational message
func (d *Display) PrintInfo(message string) {
	if !d.QuietMode {
		blue := color.New(color.FgBlue)
		blue.Println(message)
	}
}

// PrintSuccess prints a success message
func (d *Display) PrintSuccess(message string) {
	if !d.QuietMode {
		green := color.New(color.FgGreen)
		green.Println(message)
	}
}

// PrintWarning prints a warning message
func (d *Display) PrintWarning(message string) {
	if !d.QuietMode {
		yellow := color.New(color.FgYellow)
		yellow.Println(message)
	}
}

// PrintError prints an error message
func (d *Display) PrintError(message string) {
	if !d.QuietMode {
		red := color.New(color.FgRed)
		red.Println(message)
	}
}

// PrintHighlight prints a highlighted message
func (d *Display) PrintHighlight(message string) {
	if !d.QuietMode {
		cyan := color.New(color.FgCyan)
		cyan.Println(message)
	}
}

// PrintBold prints a message in bold text
func (d *Display) PrintBold(message string) {
	if !d.QuietMode {
		bold := color.New(color.Bold)
		bold.Println(message)
	}
}

// DebugLog logs debug messages if debug mode is enabled
func (d *Display) DebugLog(message string) {
	if d.Debug {
		gray := color.New(color.FgHiBlack)
		gray.Printf("[DEBUG] %s\n", message)
	}
}

func (d *Display) PrintJson(message string) {
	fmt.Print(message)
}
