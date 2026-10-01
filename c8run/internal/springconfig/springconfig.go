/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Package springconfig reads Spring Boot config files the way c8run needs them: YAML and
// .properties into one nested map shape, and config directories in Spring's file precedence.
package springconfig

import (
	"bufio"
	"bytes"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"gopkg.in/yaml.v3"
)

// DirectoryFiles are the files Spring loads from a config directory, highest precedence
// first: within one location .properties wins over YAML.
var DirectoryFiles = []string{"application.properties", "application.yml", "application.yaml"}

// FilesIn returns the config files Spring would load from a --config location, highest
// precedence first. A directory always lists application.yaml (it may not exist yet).
func FilesIn(location string) []string {
	info, err := os.Stat(location)
	if err != nil || !info.IsDir() {
		return []string{location}
	}
	var files []string
	for _, name := range DirectoryFiles {
		path := filepath.Join(location, name)
		if _, err := os.Stat(path); err == nil || name == "application.yaml" {
			files = append(files, path)
		}
	}
	return files
}

// Load reads a YAML or .properties file into a nested map. Dotted keys are expanded in both
// formats, and "true"/"false" in .properties become booleans, so callers can walk one shape.
func Load(path string) (map[string]any, bool) {
	content, err := os.ReadFile(path)
	if err != nil {
		return nil, false
	}
	if strings.EqualFold(filepath.Ext(path), ".properties") {
		return parseProperties(content), true
	}
	if len(bytes.TrimSpace(content)) == 0 {
		return map[string]any{}, true
	}
	var root map[string]any
	if yaml.Unmarshal(content, &root) != nil {
		return nil, false
	}
	return expandDottedKeys(root), true
}

// Lookup walks a nested map by keys.
func Lookup(root map[string]any, keys ...string) (any, bool) {
	var current any = root
	for _, key := range keys {
		m, ok := current.(map[string]any)
		if !ok {
			return nil, false
		}
		if current, ok = m[key]; !ok {
			return nil, false
		}
	}
	return current, true
}

func parseProperties(content []byte) map[string]any {
	root := map[string]any{}
	scanner := bufio.NewScanner(bytes.NewReader(content))
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") || strings.HasPrefix(line, "!") {
			continue
		}
		index := strings.IndexAny(line, "=:")
		if index <= 0 {
			continue
		}
		key := strings.TrimSpace(line[:index])
		raw := strings.TrimSpace(line[index+1:])
		var value any = raw
		if b, err := strconv.ParseBool(raw); err == nil {
			value = b
		}
		set(root, strings.Split(key, "."), value)
	}
	return root
}

func expandDottedKeys(in map[string]any) map[string]any {
	out := map[string]any{}
	for key, value := range in {
		if nested, ok := value.(map[string]any); ok {
			value = expandDottedKeys(nested)
		}
		set(out, strings.Split(key, "."), value)
	}
	return out
}

func set(root map[string]any, keys []string, value any) {
	current := root
	for _, key := range keys[:len(keys)-1] {
		next, ok := current[key].(map[string]any)
		if !ok {
			next = map[string]any{}
			current[key] = next
		}
		current = next
	}
	last := keys[len(keys)-1]
	if existing, ok := current[last].(map[string]any); ok {
		if incoming, ok := value.(map[string]any); ok {
			for k, v := range incoming {
				existing[k] = v
			}
			return
		}
	}
	current[last] = value
}
