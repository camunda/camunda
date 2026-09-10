/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package secrets

import (
	"bytes"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/gofrs/flock"
	"github.com/joho/godotenv"
)

const (
	DirectoryName       = "secrets"
	DirectoryEnv        = "C8RUN_SECRETS_DIR"
	ModeEnv             = "C8RUN_SECRETS_MODE"
	secretReferenceBase = "camunda.secrets."
	maxReferenceLength  = 256
	MaxSecretSize       = 1024 * 1024
	MaxImportSize       = 10 * 1024 * 1024
	MaxImportCount      = 1000
)

type Store struct {
	directory  string
	baseDir    string
	resolveErr error
}

func New(baseDir string) *Store {
	directory, err := ResolveDirectory(baseDir)
	return &Store{directory: directory, baseDir: baseDir, resolveErr: err}
}

func ResolveDirectory(baseDir string) (string, error) {
	mode, err := Mode()
	if err != nil {
		return "", err
	}
	if mode == "external" {
		return "", errors.New("local secret commands are disabled because C8RUN_SECRETS_MODE=external")
	}
	directory := strings.TrimSpace(os.Getenv(DirectoryEnv))
	if directory == "" {
		return DefaultDirectory()
	}
	if !filepath.IsAbs(directory) {
		directory = filepath.Join(baseDir, directory)
	}
	return filepath.Clean(directory), nil
}

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

func DefaultDirectory() (string, error) {
	return defaultDirectory()
}

func (s *Store) Ensure() error {
	if s.resolveErr != nil {
		return s.resolveErr
	}
	return ensureDirectory(s.directory, s.baseDir)
}

func (s *Store) Directory() (string, error) {
	if s.resolveErr != nil {
		return "", s.resolveErr
	}
	return s.directory, nil
}

func (s *Store) Exists(name string) (bool, error) {
	if err := ValidateName(name); err != nil {
		return false, err
	}
	if s.resolveErr != nil {
		return false, s.resolveErr
	}
	info, err := os.Lstat(filepath.Join(s.directory, name))
	if errors.Is(err, os.ErrNotExist) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return info.Mode().IsRegular(), nil
}

func ValidateName(name string) error {
	if name == "" {
		return errors.New("secret name must not be empty")
	}
	if len(secretReferenceBase)+len(name) > maxReferenceLength {
		return fmt.Errorf("secret name must not exceed %d characters", maxReferenceLength-len(secretReferenceBase))
	}
	for _, character := range name {
		if (character >= 'a' && character <= 'z') ||
			(character >= 'A' && character <= 'Z') ||
			(character >= '0' && character <= '9') ||
			character == '_' || character == '-' {
			continue
		}
		return errors.New("secret name may contain only letters, numbers, underscores, and dashes")
	}
	if isWindowsDeviceName(name) {
		return errors.New("secret name is reserved by Windows")
	}
	return nil
}

func (s *Store) Set(name string, value []byte) error {
	return s.SetMany(map[string][]byte{name: value})
}

func (s *Store) SetMany(values map[string][]byte) error {
	names := make([]string, 0, len(values))
	seen := make(map[string]string, len(values))
	for name, value := range values {
		if err := ValidateName(name); err != nil {
			return fmt.Errorf("invalid secret name %q: %w", name, err)
		}
		if len(value) > MaxSecretSize {
			return fmt.Errorf("secret %q must not exceed %d bytes", name, MaxSecretSize)
		}
		folded := strings.ToLower(name)
		if other, exists := seen[folded]; exists {
			return fmt.Errorf("secret names %q and %q differ only by case", other, name)
		}
		seen[folded] = name
		names = append(names, name)
	}
	sort.Strings(names)

	if err := s.Ensure(); err != nil {
		return err
	}
	lock := flock.New(filepath.Join(s.directory, ".c8run-secrets.lock"))
	if err := lock.Lock(); err != nil {
		return fmt.Errorf("failed to lock local secrets directory: %w", err)
	}
	defer func() { _ = lock.Unlock() }()
	for _, name := range names {
		if err := s.rejectCaseCollision(name); err != nil {
			return err
		}
	}
	type previousValue struct {
		existed bool
		value   []byte
	}
	previous := make(map[string]previousValue, len(names))
	for _, name := range names {
		path := filepath.Join(s.directory, name)
		info, err := os.Lstat(path)
		if errors.Is(err, os.ErrNotExist) {
			previous[name] = previousValue{}
			continue
		}
		if err != nil {
			return fmt.Errorf("failed to inspect secret %q: %w", name, err)
		}
		if !info.Mode().IsRegular() {
			return fmt.Errorf("secret %q is not a regular file", name)
		}
		content, err := os.ReadFile(path)
		if err != nil {
			return fmt.Errorf("failed to prepare secret %q for update", name)
		}
		previous[name] = previousValue{existed: true, value: content}
	}
	var committed []string
	for _, name := range names {
		if err := atomicWrite(filepath.Join(s.directory, name), values[name]); err != nil {
			committed = append(committed, name)
			var rollbackErr error
			for index := len(committed) - 1; index >= 0; index-- {
				committedName := committed[index]
				old := previous[committedName]
				if old.existed {
					if restoreErr := atomicWrite(filepath.Join(s.directory, committedName), old.value); restoreErr != nil {
						rollbackErr = errors.Join(rollbackErr, restoreErr)
					}
				} else {
					if removeErr := os.Remove(filepath.Join(s.directory, committedName)); removeErr != nil && !errors.Is(removeErr, os.ErrNotExist) {
						rollbackErr = errors.Join(rollbackErr, removeErr)
					}
				}
			}
			if rollbackErr != nil {
				return fmt.Errorf("failed to save secret %q and restore the previous values: %w", name, errors.Join(err, rollbackErr))
			}
			return fmt.Errorf("failed to save secret %q: %w", name, err)
		}
		committed = append(committed, name)
	}
	return nil
}

func (s *Store) List() ([]string, error) {
	if err := s.Ensure(); err != nil {
		return nil, err
	}
	entries, err := os.ReadDir(s.directory)
	if err != nil {
		return nil, fmt.Errorf("failed to list local secrets: %w", err)
	}

	names := make([]string, 0, len(entries))
	for _, entry := range entries {
		info, err := entry.Info()
		if err != nil || !info.Mode().IsRegular() || ValidateName(entry.Name()) != nil {
			continue
		}
		names = append(names, entry.Name())
	}
	sort.Strings(names)
	return names, nil
}

func (s *Store) Delete(name string) error {
	if err := ValidateName(name); err != nil {
		return err
	}
	if err := s.Ensure(); err != nil {
		return err
	}
	lock := flock.New(filepath.Join(s.directory, ".c8run-secrets.lock"))
	if err := lock.Lock(); err != nil {
		return fmt.Errorf("failed to lock local secrets directory: %w", err)
	}
	defer func() { _ = lock.Unlock() }()
	return s.deleteUnlocked(name)
}

func (s *Store) deleteUnlocked(name string) error {
	path := filepath.Join(s.directory, name)
	info, err := os.Lstat(path)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return fmt.Errorf("secret %q does not exist", name)
		}
		return fmt.Errorf("failed to inspect secret %q: %w", name, err)
	}
	if !info.Mode().IsRegular() {
		return fmt.Errorf("secret %q is not a regular file", name)
	}
	if err := os.Remove(path); err != nil {
		return fmt.Errorf("failed to delete secret %q: %w", name, err)
	}
	return nil
}

func (s *Store) DeleteAll() ([]string, error) {
	if err := s.Ensure(); err != nil {
		return nil, err
	}
	lock := flock.New(filepath.Join(s.directory, ".c8run-secrets.lock"))
	if err := lock.Lock(); err != nil {
		return nil, fmt.Errorf("failed to lock local secrets directory: %w", err)
	}
	defer func() { _ = lock.Unlock() }()
	entries, err := os.ReadDir(s.directory)
	if err != nil {
		return nil, fmt.Errorf("failed to list local secrets: %w", err)
	}
	var names []string
	for _, entry := range entries {
		info, err := entry.Info()
		if err == nil && info.Mode().IsRegular() && ValidateName(entry.Name()) == nil {
			names = append(names, entry.Name())
		}
	}
	sort.Strings(names)
	previous := make(map[string][]byte, len(names))
	for _, name := range names {
		content, err := os.ReadFile(filepath.Join(s.directory, name))
		if err != nil {
			return nil, fmt.Errorf("failed to prepare secret %q for deletion", name)
		}
		previous[name] = content
	}
	var deleted []string
	for _, name := range names {
		if err := s.deleteUnlocked(name); err != nil {
			var rollbackErr error
			for index := len(deleted) - 1; index >= 0; index-- {
				deletedName := deleted[index]
				if restoreErr := atomicWrite(filepath.Join(s.directory, deletedName), previous[deletedName]); restoreErr != nil {
					rollbackErr = errors.Join(rollbackErr, restoreErr)
				}
			}
			if rollbackErr != nil {
				return nil, fmt.Errorf("failed to delete secret %q and restore previously deleted values: %w", name, errors.Join(err, rollbackErr))
			}
			return nil, err
		}
		deleted = append(deleted, name)
	}
	return names, nil
}

func (s *Store) Import(reader io.Reader) ([]string, error) {
	values, err := parseDotenv(io.LimitReader(reader, MaxImportSize+1))
	if err != nil {
		return nil, err
	}

	names := make([]string, 0, len(values))
	for name := range values {
		if err := ValidateName(name); err != nil {
			return nil, fmt.Errorf("invalid secret name %q: %w", name, err)
		}
		names = append(names, name)
	}
	if len(names) > MaxImportCount {
		return nil, fmt.Errorf("dotenv input must not contain more than %d secrets", MaxImportCount)
	}
	sort.Strings(names)
	if err := s.Ensure(); err != nil {
		return nil, err
	}
	existing, err := s.List()
	if err != nil {
		return nil, err
	}
	known := make(map[string]string, len(existing)+len(names))
	for _, name := range existing {
		known[strings.ToLower(name)] = name
	}
	for _, name := range names {
		folded := strings.ToLower(name)
		if other, exists := known[folded]; exists && other != name {
			return nil, fmt.Errorf("secret names %q and %q differ only by case", other, name)
		}
		known[folded] = name
	}

	batch := make(map[string][]byte, len(values))
	for name, value := range values {
		batch[name] = []byte(value)
	}
	if err := s.SetMany(batch); err != nil {
		return nil, err
	}
	return names, nil
}

func (s *Store) rejectCaseCollision(name string) error {
	entries, err := os.ReadDir(s.directory)
	if err != nil {
		return fmt.Errorf("failed to inspect local secrets: %w", err)
	}
	for _, entry := range entries {
		if strings.EqualFold(entry.Name(), name) && entry.Name() != name {
			return fmt.Errorf("secret %q conflicts with existing secret %q on case-insensitive filesystems", name, entry.Name())
		}
	}
	return nil
}

func isWindowsDeviceName(name string) bool {
	upper := strings.ToUpper(name)
	switch upper {
	case "CON", "PRN", "AUX", "NUL", "CLOCK$":
		return true
	}
	if len(upper) == 4 && (strings.HasPrefix(upper, "COM") || strings.HasPrefix(upper, "LPT")) {
		return upper[3] >= '1' && upper[3] <= '9'
	}
	return false
}

func parseDotenv(reader io.Reader) (map[string]string, error) {
	content, err := io.ReadAll(io.LimitReader(reader, MaxImportSize+1))
	if err != nil {
		return nil, errors.New("failed to read dotenv input")
	}
	if len(content) > MaxImportSize {
		return nil, fmt.Errorf("dotenv input must not exceed %d bytes", MaxImportSize)
	}

	placeholder := make([]byte, 16)
	if _, err := rand.Read(placeholder); err != nil {
		return nil, errors.New("failed to prepare dotenv import")
	}
	dollarToken := "C8RUN_DOLLAR_" + hex.EncodeToString(placeholder)
	for bytes.Contains(content, []byte(dollarToken)) {
		if _, err := rand.Read(placeholder); err != nil {
			return nil, errors.New("failed to prepare dotenv import")
		}
		dollarToken = "C8RUN_DOLLAR_" + hex.EncodeToString(placeholder)
	}

	aliases := make(map[string]string)
	seen := make(map[string]struct{})
	lines := bytes.SplitAfter(content, []byte("\n"))
	var openQuote byte
	for index, line := range lines {
		if openQuote == 0 {
			name, keyStart, keyEnd, ok := dotenvKey(line)
			if ok {
				if _, exists := seen[name]; exists {
					return nil, fmt.Errorf("duplicate secret name %q on line %d", name, index+1)
				}
				seen[name] = struct{}{}
				alias := fmt.Sprintf("C8RUN_SECRET_%d", index)
				aliases[alias] = name
				line = append(append(append([]byte{}, line[:keyStart]...), alias...), line[keyEnd:]...)
				lines[index] = line
			}
		}
		openQuote = dotenvQuoteState(line, openQuote)
	}

	rewritten := bytes.ReplaceAll(bytes.Join(lines, nil), []byte("$"), []byte(dollarToken))
	parsed, err := godotenv.Parse(bytes.NewReader(rewritten))
	if err != nil {
		return nil, errors.New("invalid dotenv input")
	}
	values := make(map[string]string, len(parsed))
	for alias, value := range parsed {
		name, ok := aliases[alias]
		if !ok {
			return nil, errors.New("invalid dotenv input")
		}
		value = strings.ReplaceAll(value, dollarToken, "$")
		if len(value) > MaxSecretSize {
			return nil, fmt.Errorf("secret %q must not exceed %d bytes", name, MaxSecretSize)
		}
		values[name] = value
	}
	return values, nil
}

func dotenvQuoteState(line []byte, openQuote byte) byte {
	if openQuote == 0 {
		separator := bytes.IndexByte(line, '=')
		if separator < 0 {
			return 0
		}
		line = bytes.TrimLeft(line[separator+1:], " \t")
		if len(line) == 0 || (line[0] != '\'' && line[0] != '"') {
			return 0
		}
		openQuote = line[0]
		line = line[1:]
	}
	escaped := false
	for _, character := range line {
		if character == '\\' && openQuote == '"' && !escaped {
			escaped = true
			continue
		}
		if character == openQuote && !escaped {
			return 0
		}
		escaped = false
	}
	return openQuote
}

func dotenvKey(line []byte) (string, int, int, bool) {
	trimmed := bytes.TrimSpace(line)
	if len(trimmed) == 0 || trimmed[0] == '#' {
		return "", 0, 0, false
	}
	start := bytes.Index(line, trimmed)
	if bytes.HasPrefix(trimmed, []byte("export")) && len(trimmed) > len("export") && (trimmed[len("export")] == ' ' || trimmed[len("export")] == '\t') {
		trimmed = bytes.TrimLeft(trimmed[len("export"):], " \t")
		start = bytes.Index(line, trimmed)
	}
	separator := bytes.IndexByte(trimmed, '=')
	if separator < 1 {
		return "", 0, 0, false
	}
	key := bytes.TrimSpace(trimmed[:separator])
	keyStart := start + bytes.Index(trimmed, key)
	return string(key), keyStart, keyStart + len(key), true
}

func TrimInputLineEnding(value []byte) []byte {
	value = []byte(strings.TrimSuffix(string(value), "\n"))
	return []byte(strings.TrimSuffix(string(value), "\r"))
}
