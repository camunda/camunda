package search

import (
	"camunda.com/helmunusedvalues/pkg/keys"
	"camunda.com/helmunusedvalues/pkg/output"
	"camunda.com/helmunusedvalues/pkg/patterns"
)

type Finder struct {
	UseRipgrep   bool
	TemplatesDir string
	Registry     *patterns.Registry
	Parallelism  int // Number of parallel workers (0 = auto)
	Display      *output.Display
}

func NewFinder(templatesDir string, registry *patterns.Registry, useRipgrep bool, display *output.Display) *Finder {
	return &Finder{
		TemplatesDir: templatesDir,
		Registry:     registry,
		UseRipgrep:   useRipgrep,
		Parallelism:  0,
		Display:      display,
	}
}

func (f *Finder) FindUnusedKeys(keys []string, showProgress bool) ([]keys.KeyUsage, error) {
	return f.analyzeKeys(keys, showProgress)
}
