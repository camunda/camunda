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
	"gopkg.in/yaml.v3"
)

const (
	FileEnv            = "C8RUN_TENANTS_FILE"
	ModeEnv            = "C8RUN_TENANTS_MODE"
	FileName           = "physical-tenants.yaml"
	credentialsSuffix  = ".credentials"
	currentFileVersion = 1
)

// Tenant is a persisted physical tenant. Passwords are never stored here.
type Tenant struct {
	ID string `yaml:"id"`
	// Username is empty when the tenant reuses the --username/--password login.
	Username string `yaml:"username,omitempty"`
	// NoConnectors disables the dedicated connectors runtime for this tenant.
	NoConnectors bool `yaml:"noConnectors,omitempty"`
}

type document struct {
	Version int      `yaml:"version"`
	Tenants []Tenant `yaml:"tenants"`
}

// Store persists tenants in a YAML file and per-tenant passwords in a sibling 0600 file.
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

func (s *Store) credentialsPath() string { return s.path + credentialsSuffix }

// List returns the persisted tenants sorted by id. A missing file means no tenants.
func (s *Store) List() ([]Tenant, error) {
	content, err := os.ReadFile(s.path)
	if errors.Is(err, os.ErrNotExist) {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to read %s: %w", s.path, err)
	}
	var doc document
	if err := yaml.Unmarshal(content, &doc); err != nil {
		return nil, fmt.Errorf("%s is not valid YAML (fix or delete it, or run `c8run tenants reset`): %w", s.path, err)
	}
	for _, t := range doc.Tenants {
		if err := ValidateID(t.ID); err != nil {
			return nil, fmt.Errorf("%s: %w", s.path, err)
		}
	}
	sort.Slice(doc.Tenants, func(i, j int) bool { return doc.Tenants[i].ID < doc.Tenants[j].ID })
	return doc.Tenants, nil
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
	tenants, err := s.List()
	if err != nil {
		return err
	}
	existing := map[string]bool{}
	for _, t := range tenants {
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
	if len(passwords) > 0 {
		creds, err := s.readCredentials()
		if err != nil {
			return err
		}
		for id, pw := range passwords {
			creds[id] = pw
		}
		if err := s.writeCredentials(creds); err != nil {
			return err
		}
	}
	return s.write(append(tenants, newTenants...))
}

// Remove deletes tenants (and their stored passwords). Unknown ids are an error.
func (s *Store) Remove(ids []string) error {
	tenants, err := s.List()
	if err != nil {
		return err
	}
	drop := map[string]bool{}
	for _, id := range ids {
		drop[id] = true
	}
	kept := tenants[:0]
	for _, t := range tenants {
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
	creds, err := s.readCredentials()
	if err != nil {
		return err
	}
	for _, id := range ids {
		delete(creds, id)
	}
	if err := s.writeCredentials(creds); err != nil {
		return err
	}
	return s.write(kept)
}

// Reset removes every tenant and stored credential.
func (s *Store) Reset() error {
	for _, p := range []string{s.path, s.credentialsPath()} {
		if err := os.Remove(p); err != nil && !errors.Is(err, os.ErrNotExist) {
			return err
		}
	}
	return nil
}

// Password returns the stored password for a tenant with its own login.
func (s *Store) Password(id string) (string, bool, error) {
	creds, err := s.readCredentials()
	if err != nil {
		return "", false, err
	}
	pw, ok := creds[id]
	return pw, ok, nil
}

func (s *Store) write(tenants []Tenant) error {
	sort.Slice(tenants, func(i, j int) bool { return tenants[i].ID < tenants[j].ID })
	content, err := yaml.Marshal(document{Version: currentFileVersion, Tenants: tenants})
	if err != nil {
		return err
	}
	header := "# Physical tenants managed by `c8run tenants`. Edit with the CLI rather than by hand.\n"
	return atomicWrite(s.path, append([]byte(header), content...), 0o644)
}

func (s *Store) readCredentials() (map[string]string, error) {
	creds := map[string]string{}
	content, err := os.ReadFile(s.credentialsPath())
	if errors.Is(err, os.ErrNotExist) {
		return creds, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to read tenant credentials: %w", err)
	}
	if err := yaml.Unmarshal(content, &creds); err != nil {
		return nil, fmt.Errorf("tenant credentials file %s is corrupt: %w", s.credentialsPath(), err)
	}
	if creds == nil {
		creds = map[string]string{}
	}
	return creds, nil
}

func (s *Store) writeCredentials(creds map[string]string) error {
	if len(creds) == 0 {
		err := os.Remove(s.credentialsPath())
		if errors.Is(err, os.ErrNotExist) {
			return nil
		}
		return err
	}
	content, err := yaml.Marshal(creds)
	if err != nil {
		return err
	}
	return atomicWrite(s.credentialsPath(), content, 0o600)
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
