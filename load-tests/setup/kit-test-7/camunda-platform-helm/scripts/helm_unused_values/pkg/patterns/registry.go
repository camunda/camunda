package patterns

import (
	"fmt"
	"os"
	"regexp"
)

// Registry holds information about search patterns
type Registry struct {
	Names         []string
	Descriptions  map[string]string
	Regexes       map[string]string
	KeysFiles     map[string]string
	LocationFiles map[string]string
	CompiledRegex map[string]*regexp.Regexp // Cache for compiled regex patterns
}

// New creates a new pattern registry
func New() *Registry {
	return &Registry{
		Names:         []string{},
		Descriptions:  make(map[string]string),
		Regexes:       make(map[string]string),
		KeysFiles:     make(map[string]string),
		LocationFiles: make(map[string]string),
		CompiledRegex: make(map[string]*regexp.Regexp),
	}
}

// Register adds a pattern to the registry
func (r *Registry) Register(name, description, regex string) error {
	r.Names = append(r.Names, name)
	r.Descriptions[name] = description
	r.Regexes[name] = regex

	// Compile the regex pattern for later use
	compiled, err := regexp.Compile(regex)
	if err == nil {
		r.CompiledRegex[name] = compiled
	}

	// Create temporary files for each pattern
	keysFile, err := os.CreateTemp("", fmt.Sprintf("keys_%s_", name))
	if err != nil {
		return fmt.Errorf("failed to create keys file: %w", err)
	}
	keysFile.Close()

	locationsFile, err := os.CreateTemp("", fmt.Sprintf("locations_%s_", name))
	if err != nil {
		return fmt.Errorf("failed to create locations file: %w", err)
	}
	locationsFile.Close()

	r.KeysFiles[name] = keysFile.Name()
	r.LocationFiles[name] = locationsFile.Name()

	return nil
}

// CleanUp removes all temporary files
func (r *Registry) CleanUp() {
	for _, name := range r.Names {
		if keysFile, ok := r.KeysFiles[name]; ok {
			os.Remove(keysFile)
		}
		if locationsFile, ok := r.LocationFiles[name]; ok {
			os.Remove(locationsFile)
		}
	}
}

// See testcases for examples of how to use this
func (r *Registry) RegisterBuiltins() error {
	patterns := []struct {
		name        string
		description string
		regex       string
	}{
		{
			"toyaml",
			"keys used with toYaml",
			`toYaml\s+\.Values\.`,
		},
		{
			"security_context",
			"keys used with common.compatibility.renderSecurityContext",
			`include\s+\"common\.compatibility\.renderSecurityContext\".*\.Values\.`,
		},
		{
			"subChartImagePullSecrets",
			"keys used with camundaPlatform subchart image pull secrets",
			`include\s+\"camundaPlatform\.subChartImagePullSecrets\".*\.Values\.`,
		},
		{
			"imageByParams",
			"keys used with camundaPlatform subchart image pull secrets",
			`include\s+\"camundaPlatform\.imageByParams\".*\.Values\.`,
		},
		{
			"with_context",
			"keys used with - with",
			`with\s+\.Values\.`,
		},
		{
			"include_context",
			"keys used with include and context",
			`include\s+.*\.Values\.`,
		},
	}

	for _, p := range patterns {
		if err := r.Register(p.name, p.description, p.regex); err != nil {
			return fmt.Errorf("failed to register pattern %s: %w", p.name, err)
		}
	}

	return nil
}
