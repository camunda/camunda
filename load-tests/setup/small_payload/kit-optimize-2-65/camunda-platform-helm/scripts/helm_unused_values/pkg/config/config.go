package config

// Config holds all the runtime settings for the application
type Config struct {
	TemplatesDir     string
	NoColors         bool
	ShowAllKeys      bool
	JSONOutput       bool
	ExitCodeOnUnused int
	QuietMode        bool
	FilterPattern    string
	Debug            bool
	UseRipgrep       bool
	SearchTool       string // Preferred search tool (ripgrep or grep)
	Parallelism      int    // Number of parallel workers (0 = auto)
}

// New creates a new configuration with default values
func New() *Config {
	return &Config{
		ExitCodeOnUnused: 0,  // Default: Don't fail on unused values
		SearchTool:       "", // Empty means auto-detect (ripgrep if available)
		Parallelism:      0,  // Default: Auto (set based on CPU cores)
	}
}
