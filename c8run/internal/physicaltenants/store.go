/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package physicaltenants

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"

	localsecrets "github.com/camunda/camunda/c8run/internal/secrets"
	"github.com/gofrs/flock"
	"gopkg.in/yaml.v3"
)

const (
	FileEnv            = "C8RUN_TENANTS_FILE"
	ModeEnv            = "C8RUN_TENANTS_MODE"
	FileName           = "physical-tenants.yaml"
	lockSuffix         = ".lock"
	currentFileVersion = 1
)

// Tenant is a persisted physical tenant.
type Tenant struct {
	ID string `yaml:"id"`
	// Username is empty when the tenant reuses the --username/--password login.
	Username string `yaml:"username,omitempty"`
	// NoConnectors disables the dedicated connectors runtime for this tenant.
	NoConnectors bool `yaml:"noConnectors,omitempty"`
}

// document is the whole tenants file. Tenants and their passwords live in one document so
// every change is a single atomic rename: no crash can leave them disagreeing. The file is
// therefore always written with owner-only permissions.
type document struct {
	Version   int               `yaml:"version"`
	Tenants   []Tenant          `yaml:"tenants"`
	Passwords map[string]string `yaml:"passwords,omitempty"`
}

// Store persists tenants and per-tenant passwords in one owner-only YAML file.
type Store struct {
	path string
}

// Mode returns "local" (default) or "external" (tenants are managed in the user's --config).
func Mode() (string, error) {
	mode := strings.ToLower(strings.TrimSpace(os.Getenv(ModeEnv)))
	if mode == "" {
		return "local", nil
	}
	if mode != "local" && mode != "external" {
		return "", fmt.Errorf("%s must be local or external", ModeEnv)
	}
	return mode, nil
}

// ResolvePath returns the tenants file: C8RUN_TENANTS_FILE, or physical-tenants.yaml in the
// per-user c8run data directory (next to the local secrets directory).
func ResolvePath(baseDir string) (string, error) {
	if path := strings.TrimSpace(os.Getenv(FileEnv)); path != "" {
		if !filepath.IsAbs(path) {
			path = filepath.Join(baseDir, path)
		}
		return filepath.Clean(path), nil
	}
	secretsDir, err := localsecrets.DefaultDirectory()
	if err != nil {
		return "", fmt.Errorf("failed to resolve the c8run data directory; set %s: %w", FileEnv, err)
	}
	return filepath.Join(filepath.Dir(secretsDir), FileName), nil
}

func NewStore(path string) *Store { return &Store{path: path} }

func (s *Store) Path() string { return s.path }

// read loads and validates the file. A missing file is an empty document.
func (s *Store) read() (document, error) {
	doc := document{Version: currentFileVersion, Passwords: map[string]string{}}
	content, err := os.ReadFile(s.path)
	if errors.Is(err, os.ErrNotExist) {
		return doc, nil
	}
	if err != nil {
		return doc, fmt.Errorf("failed to read %s: %w", s.path, err)
	}
	if err := yaml.Unmarshal(content, &doc); err != nil {
		return doc, fmt.Errorf("%s is not valid YAML (fix or delete it, or run `c8run tenants reset`): %w", s.path, err)
	}
	seen := map[string]bool{}
	for _, t := range doc.Tenants {
		if err := ValidateID(t.ID); err != nil {
			return doc, fmt.Errorf("%s: %w", s.path, err)
		}
		if seen[t.ID] {
			return doc, fmt.Errorf("%s: physical tenant %q is listed more than once; remove the duplicate entry", s.path, t.ID)
		}
		seen[t.ID] = true
	}
	if doc.Passwords == nil {
		doc.Passwords = map[string]string{}
	}
	sort.Slice(doc.Tenants, func(i, j int) bool { return doc.Tenants[i].ID < doc.Tenants[j].ID })
	return doc, nil
}

// List returns the persisted tenants sorted by id. A missing file means no tenants.
func (s *Store) List() ([]Tenant, error) {
	doc, err := s.read()
	return doc.Tenants, err
}

// Snapshot returns the tenants and their stored passwords from one read of one file.
func (s *Store) Snapshot() ([]Tenant, map[string]string, error) {
	doc, err := s.read()
	return doc.Tenants, doc.Passwords, err
}

// Get returns one tenant.
func (s *Store) Get(id string) (Tenant, bool, error) {
	tenants, err := s.List()
	if err != nil {
		return Tenant{}, false, err
	}
	for _, t := range tenants {
		if t.ID == id {
			return t, true, nil
		}
	}
	return Tenant{}, false, nil
}

// Add persists new tenants. It fails without writing anything if any id already exists.
func (s *Store) Add(newTenants []Tenant, passwords map[string]string) error {
	return s.update(func(doc *document) error {
		existing := map[string]bool{}
		for _, t := range doc.Tenants {
			existing[t.ID] = true
		}
		for _, t := range newTenants {
			if err := ValidateID(t.ID); err != nil {
				return err
			}
			if existing[t.ID] {
				return fmt.Errorf("physical tenant %q already exists; remove it first or pick another ID", t.ID)
			}
			existing[t.ID] = true
		}
		doc.Tenants = append(doc.Tenants, newTenants...)
		for id, pw := range passwords {
			doc.Passwords[id] = pw
		}
		return nil
	})
}

// Remove deletes tenants and their stored passwords. Unknown ids are an error.
func (s *Store) Remove(ids []string) error {
	return s.update(func(doc *document) error {
		drop := map[string]bool{}
		for _, id := range ids {
			drop[id] = true
		}
		kept := make([]Tenant, 0, len(doc.Tenants))
		for _, t := range doc.Tenants {
			if drop[t.ID] {
				delete(drop, t.ID)
				continue
			}
			kept = append(kept, t)
		}
		if len(drop) > 0 {
			var missing []string
			for id := range drop {
				missing = append(missing, id)
			}
			sort.Strings(missing)
			return fmt.Errorf("unknown physical tenant(s): %s (run `c8run tenants list`)", strings.Join(missing, ", "))
		}
		doc.Tenants = kept
		for _, id := range ids {
			delete(doc.Passwords, id)
		}
		return nil
	})
}

// Reset removes every tenant and stored password.
func (s *Store) Reset() error {
	return s.locked(func() error {
		if err := os.Remove(s.path); err != nil && !errors.Is(err, os.ErrNotExist) {
			return err
		}
		return nil
	})
}

// Password returns the stored password for a tenant with its own login.
func (s *Store) Password(id string) (string, bool, error) {
	doc, err := s.read()
	if err != nil {
		return "", false, err
	}
	pw, ok := doc.Passwords[id]
	return pw, ok, nil
}

// update applies a change to the whole document under the lock and commits it with one
// atomic rename; nothing is written if the change returns an error.
func (s *Store) update(change func(*document) error) error {
	return s.locked(func() error {
		doc, err := s.read()
		if err != nil {
			return err
		}
		if err := change(&doc); err != nil {
			return err
		}
		return s.write(doc)
	})
}

// locked serializes every mutation of the tenants file across processes.
func (s *Store) locked(operation func() error) error {
	if err := os.MkdirAll(filepath.Dir(s.path), 0o700); err != nil {
		return fmt.Errorf("failed to create %s: %w", filepath.Dir(s.path), err)
	}
	lock := flock.New(s.path + lockSuffix)
	if err := lock.Lock(); err != nil {
		return fmt.Errorf("failed to lock %s: %w", s.path, err)
	}
	defer func() { _ = lock.Unlock() }()
	return operation()
}

func (s *Store) write(doc document) error {
	sort.Slice(doc.Tenants, func(i, j int) bool { return doc.Tenants[i].ID < doc.Tenants[j].ID })
	doc.Version = currentFileVersion
	if len(doc.Passwords) == 0 {
		doc.Passwords = nil
	}
	content, err := yaml.Marshal(doc)
	if err != nil {
		return err
	}
	header := "# Physical tenants managed by `c8run tenants`. Edit with the CLI rather than by hand.\n" +
		"# Readable only by you: it holds the passwords of tenants with their own login.\n"
	return atomicWrite(s.path, append([]byte(header), content...), 0o600)
}

func atomicWrite(path string, content []byte, perm os.FileMode) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return fmt.Errorf("failed to create %s: %w", filepath.Dir(path), err)
	}
	tmp, err := os.CreateTemp(filepath.Dir(path), "."+filepath.Base(path)+".*")
	if err != nil {
		return err
	}
	tmpName := tmp.Name()
	defer func() { _ = os.Remove(tmpName) }()
	if err := tmp.Chmod(perm); err != nil && !isWindows() {
		_ = tmp.Close()
		return err
	}
	if _, err := tmp.Write(content); err != nil {
		_ = tmp.Close()
		return err
	}
	if err := tmp.Sync(); err != nil {
		_ = tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	return os.Rename(tmpName, path)
}
