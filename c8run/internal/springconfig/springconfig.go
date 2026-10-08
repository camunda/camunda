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
	"bytes"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"gopkg.in/yaml.v3"
)

// DirectoryFiles are the files Spring loads from a config directory, highest precedence
// first: within one location .properties wins over YAML.
var DirectoryFiles = []string{"application.properties", "application.yml", "application.yaml"}

// FilesIn returns the config files Spring would load from a config location for the active
// profiles, highest precedence first: profile-specific files (last profile wins), then the
// base files. A directory always lists application.yaml (it may not exist yet).
func FilesIn(location string, profiles []string) []string {
	info, err := os.Stat(location)
	if err != nil || !info.IsDir() {
		return []string{location}
	}
	var files []string
	for i := len(profiles) - 1; i >= 0; i-- {
		for _, ext := range []string{".properties", ".yml", ".yaml"} {
			path := filepath.Join(location, "application-"+profiles[i]+ext)
			if _, err := os.Stat(path); err == nil {
				files = append(files, path)
			}
		}
	}
	for _, name := range DirectoryFiles {
		path := filepath.Join(location, name)
		if _, err := os.Stat(path); err == nil || name == "application.yaml" {
			files = append(files, path)
		}
	}
	return files
}

func Paths(baseDir, userConfig string) []string {
	locations := []string{filepath.Join(baseDir, "configuration")}
	if userConfig != "" {
		candidate := filepath.Join(baseDir, userConfig)
		if _, err := os.Stat(candidate); err == nil {
			locations = []string{candidate, locations[0]}
		}
	}
	// Spring activates profiles from the base files of every location, then applies them to all.
	var baseFiles []string
	for _, location := range locations {
		baseFiles = append(baseFiles, FilesIn(location, nil)...)
	}
	active, _ := Value(baseFiles, "spring.profiles.active")
	var paths []string
	for _, location := range locations {
		paths = append(paths, FilesIn(location, splitProfiles(active))...)
	}
	return paths
}

func Value(paths []string, property string) (string, bool) {
	for _, source := range []string{"JAVA_OPTS", "JDK_JAVA_OPTIONS"} {
		if value := systemProperty(os.Getenv(source), property); value != "" {
			return value, true
		}
	}
	env := strings.ToUpper(strings.ReplaceAll(property, ".", "_"))
	for _, name := range []string{strings.ReplaceAll(env, "-", ""), strings.ReplaceAll(env, "-", "_")} {
		if value := strings.TrimSpace(os.Getenv(name)); value != "" {
			return value, true
		}
	}
	keys := strings.Split(property, ".")
	for _, path := range paths {
		root, ok := Load(path)
		if !ok {
			continue
		}
		if value, ok := Lookup(root, keys...); ok {
			if s := scalar(value); s != "" {
				return s, true
			}
		}
	}
	return "", false
}

// Bool resolves property like Value and accepts the boolean spellings Spring converts.
func Bool(paths []string, property string) (value, ok bool) {
	raw, _ := Value(paths, property)
	switch strings.ToLower(raw) {
	case "true", "on", "yes", "1":
		return true, true
	case "false", "off", "no", "0":
		return false, true
	}
	return false, false
}

func systemProperty(options, property string) string {
	value := ""
	for _, option := range strings.Fields(options) {
		name, v, ok := strings.Cut(option, "=")
		if ok && strings.HasPrefix(name, "-D") && canonical(name[2:]) == canonical(property) {
			value = strings.TrimSpace(v)
		}
	}
	return value
}

func scalar(value any) string {
	switch value.(type) {
	case nil, map[string]any, []any:
		return ""
	}
	return strings.TrimSpace(fmt.Sprint(value))
}

func canonical(name string) string {
	return strings.ToLower(strings.NewReplacer("-", "", "_", "").Replace(name))
}

func splitProfiles(value string) []string {
	var profiles []string
	for _, p := range strings.Split(value, ",") {
		if p = strings.TrimSpace(p); p != "" {
			profiles = append(profiles, p)
		}
	}
	return profiles
}

// ResolvePlaceholders expands ${NAME} and ${NAME:default} the way Spring does for the
// environment, so c8run decides on the value Camunda will actually see. Property names are
// also tried in their environment form (camunda.data.x -> CAMUNDA_DATA_X).
func ResolvePlaceholders(value string) string {
	for i := 0; i < 10 && strings.Contains(value, "${"); i++ {
		start := strings.Index(value, "${")
		end := strings.Index(value[start:], "}")
		if end < 0 {
			return value
		}
		end += start
		expr := value[start+2 : end]
		name, def, hasDefault := strings.Cut(expr, ":")
		replacement, ok := os.LookupEnv(name)
		if !ok {
			envName := strings.ToUpper(strings.NewReplacer(".", "_", "-", "").Replace(name))
			replacement, ok = os.LookupEnv(envName)
		}
		if !ok {
			if !hasDefault {
				return value
			}
			replacement = def
		}
		value = value[:start] + replacement + value[end+1:]
	}
	return value
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
	return resolveAll(expandDottedKeys(root)), true
}

func resolveAll(m map[string]any) map[string]any {
	for key, value := range m {
		switch v := value.(type) {
		case map[string]any:
			m[key] = resolveAll(v)
		case string:
			resolved := ResolvePlaceholders(v)
			if b, err := strconv.ParseBool(resolved); err == nil && resolved != v {
				m[key] = b
			} else {
				m[key] = resolved
			}
		}
	}
	return m
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
			for k, v := range m {
				if canonical(k) == canonical(key) {
					current, ok = v, true
					break
				}
			}
			if !ok {
				return nil, false
			}
		}
	}
	return current, true
}

func parseProperties(content []byte) map[string]any {
	root := map[string]any{}
	for _, line := range propertyLines(string(content)) {
		key, raw := splitProperty(line)
		if key == "" {
			continue
		}
		raw = ResolvePlaceholders(strings.TrimSpace(raw))
		var value any = raw
		if b, err := strconv.ParseBool(raw); err == nil {
			value = b
		}
		set(root, strings.Split(key, "."), value)
	}
	return root
}

func propertyLines(content string) []string {
	var lines []string
	var current strings.Builder
	continuing := false
	for _, natural := range strings.Split(strings.NewReplacer("\r\n", "\n", "\r", "\n").Replace(content), "\n") {
		natural = strings.TrimLeft(natural, " \t\f")
		if !continuing && (natural == "" || natural[0] == '#' || natural[0] == '!') {
			continue
		}
		if backslashes := len(natural) - len(strings.TrimRight(natural, "\\")); backslashes%2 == 1 {
			current.WriteString(natural[:len(natural)-1])
			continuing = true
			continue
		}
		current.WriteString(natural)
		lines = append(lines, current.String())
		current.Reset()
		continuing = false
	}
	if current.Len() > 0 {
		lines = append(lines, current.String())
	}
	return lines
}

func splitProperty(line string) (string, string) {
	end := len(line)
	for i := 0; i < len(line); i++ {
		if line[i] == '\\' {
			i++
			continue
		}
		if strings.IndexByte("=: \t\f", line[i]) >= 0 {
			end = i
			break
		}
	}
	rest := strings.TrimLeft(line[end:], " \t\f")
	if rest != "" && (rest[0] == '=' || rest[0] == ':') {
		rest = strings.TrimLeft(rest[1:], " \t\f")
	}
	return unescapeProperty(line[:end]), unescapeProperty(rest)
}

func unescapeProperty(s string) string {
	if !strings.Contains(s, "\\") {
		return s
	}
	var b strings.Builder
	for i := 0; i < len(s); i++ {
		if s[i] != '\\' || i+1 == len(s) {
			b.WriteByte(s[i])
			continue
		}
		i++
		switch s[i] {
		case 't':
			b.WriteByte('\t')
		case 'n':
			b.WriteByte('\n')
		case 'r':
			b.WriteByte('\r')
		case 'f':
			b.WriteByte('\f')
		case 'u':
			if code, err := strconv.ParseUint(s[i+1:min(i+5, len(s))], 16, 32); err == nil && i+5 <= len(s) {
				b.WriteRune(rune(code))
				i += 4
			} else {
				b.WriteByte('u')
			}
		default:
			b.WriteByte(s[i])
		}
	}
	return b.String()
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
