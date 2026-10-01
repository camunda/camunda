/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package main

import (
	"bufio"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"text/tabwriter"

	pt "github.com/camunda/camunda/c8run/internal/physicaltenants"
	"gopkg.in/yaml.v3"
)

type tenantsCommand struct {
	input        io.Reader
	output       io.Writer
	errorOutput  io.Writer
	isTerminal   func() bool
	readPassword func() ([]byte, error)
	// port is the main Camunda port used to render URLs in `list`.
	port int
}

func newTenantsCommand() *tenantsCommand {
	port := 8080
	if p, err := strconv.Atoi(os.Getenv("C8RUN_PORT")); err == nil && p > 0 {
		port = p
	}
	return &tenantsCommand{
		input:        os.Stdin,
		output:       os.Stdout,
		errorOutput:  os.Stderr,
		isTerminal:   stdinIsTerminal,
		readPassword: readSecretFromTerminal,
		port:         port,
	}
}

func isTenantsCommand(name string) bool {
	return name == "tenants" || name == "physical-tenants" || name == "pt"
}

func (c *tenantsCommand) run(baseDir string, args []string) error {
	if len(args) == 0 || args[0] == "help" || args[0] == "-h" || args[0] == "--help" {
		_, _ = fmt.Fprint(c.output, tenantsHelp)
		return nil
	}
	if len(args) == 2 && (args[1] == "help" || args[1] == "-h" || args[1] == "--help") {
		if help, ok := tenantsCommandHelp[args[0]]; ok {
			_, _ = fmt.Fprint(c.output, help)
			return nil
		}
	}
	if _, ok := tenantsCommandHelp[args[0]]; !ok {
		return fmt.Errorf("unsupported tenants operation: %s (run `c8run tenants help`)", args[0])
	}

	mode, err := pt.Mode()
	if err != nil {
		return err
	}
	path, err := pt.ResolvePath(baseDir)
	if err != nil {
		return err
	}
	if args[0] == "path" {
		if len(args) != 1 {
			return errors.New("usage: c8run tenants path")
		}
		_, _ = fmt.Fprintln(c.output, path)
		return nil
	}
	if mode == "external" {
		return fmt.Errorf("c8run tenants commands are disabled because %s=external; declare camunda.physical-tenants in your --config file instead", pt.ModeEnv)
	}
	if v := os.Getenv("CAMUNDA_VERSION"); v != "" && !pt.SupportsVersion(v) {
		return fmt.Errorf("physical tenants require Camunda 8.%d or newer; this c8run bundles Camunda %s", pt.MinCamundaMinor, v)
	}
	store := pt.NewStore(path)
	if port := pt.LastStartPort(baseDir); port > 0 && os.Getenv("C8RUN_PORT") == "" {
		c.port = port
	}

	switch args[0] {
	case "add":
		return c.add(baseDir, store, args[1:])
	case "list", "ls":
		if len(args) != 1 {
			return errors.New("usage: c8run tenants list")
		}
		return c.list(baseDir, store)
	case "remove", "rm":
		return c.remove(baseDir, store, args[1:])
	case "reset":
		return c.reset(baseDir, store, args[1:])
	}
	return nil
}

type addOptions struct {
	ids           []string
	username      string
	passwordStdin bool
	noConnectors  bool
}

func parseAddArguments(args []string) (addOptions, error) {
	var opts addOptions
	var raw []string
	for i := 0; i < len(args); i++ {
		arg := args[i]
		switch {
		case arg == "--no-connectors":
			opts.noConnectors = true
		case arg == "--password-stdin":
			opts.passwordStdin = true
		case arg == "--username":
			if i+1 >= len(args) {
				return opts, errors.New("--username requires a value")
			}
			i++
			opts.username = args[i]
		case strings.HasPrefix(arg, "--username="):
			opts.username = strings.TrimPrefix(arg, "--username=")
		case arg == "--password" || strings.HasPrefix(arg, "--password="):
			return opts, errors.New("--password is not accepted because it leaks into shell history; you will be prompted, or use --password-stdin")
		case strings.HasPrefix(arg, "-"):
			return opts, fmt.Errorf("unsupported option: %s", arg)
		default:
			raw = append(raw, arg)
		}
	}
	if len(raw) == 0 {
		return opts, errors.New("usage: c8run tenants add <id> [id...] [--username <name>] [--password-stdin] [--no-connectors]")
	}
	ids, err := pt.ParseIDList(raw)
	if err != nil {
		return opts, err
	}
	opts.ids = ids
	if opts.username != "" && strings.TrimSpace(opts.username) != opts.username {
		return opts, errors.New("--username must not have leading or trailing spaces")
	}
	if opts.passwordStdin && opts.username == "" {
		return opts, errors.New("--password-stdin requires --username")
	}
	if opts.passwordStdin && len(ids) > 1 {
		return opts, errors.New("--password-stdin accepts exactly one tenant")
	}
	return opts, nil
}

func (c *tenantsCommand) add(baseDir string, store *pt.Store, args []string) error {
	opts, err := parseAddArguments(args)
	if err != nil {
		return err
	}
	passwords := map[string]string{}
	if opts.username != "" {
		for _, id := range opts.ids {
			pw, err := c.readTenantPassword(id, opts)
			if err != nil {
				return err
			}
			passwords[id] = pw
		}
	}
	tenants := make([]pt.Tenant, 0, len(opts.ids))
	for _, id := range opts.ids {
		tenants = append(tenants, pt.Tenant{ID: id, Username: opts.username, NoConnectors: opts.noConnectors})
	}
	if err := withoutInterrupts(func() error { return store.Add(tenants, passwords) }); err != nil {
		return err
	}
	for _, t := range tenants {
		e := pt.EndpointsFor(t.ID, "http", c.port)
		login := "same login as `c8run start` (--username/--password, default demo/demo)"
		if t.Username != "" {
			login = "user " + t.Username + " (password stored locally)"
		}
		_, _ = fmt.Fprintf(c.output, "Physical tenant %s added.\n  Login:     %s\n  Operate:   %s\n  API:       %s\n", t.ID, login, e.Operate, e.REST)
		if t.NoConnectors {
			_, _ = fmt.Fprintln(c.output, "  Connectors: disabled for this tenant")
		}
	}
	c.printRestartHint(baseDir, "start", len(tenants))
	return nil
}

func (c *tenantsCommand) readTenantPassword(id string, opts addOptions) (string, error) {
	if opts.passwordStdin {
		value, err := io.ReadAll(io.LimitReader(c.input, 4096))
		if err != nil {
			return "", fmt.Errorf("failed to read password: %w", err)
		}
		pw := strings.TrimRight(string(value), "\r\n")
		if pw == "" {
			return "", errors.New("password read from stdin is empty")
		}
		return pw, nil
	}
	if !c.isTerminal() {
		return "", errors.New("interactive password input requires a terminal; use --password-stdin for automation")
	}
	for attempt := 0; attempt < 3; attempt++ {
		_, _ = fmt.Fprintf(c.errorOutput, "Password for %s in tenant %s: ", opts.username, id)
		first, err := c.readPassword()
		_, _ = fmt.Fprintln(c.errorOutput)
		if err != nil {
			return "", fmt.Errorf("failed to read password: %w", err)
		}
		if len(first) == 0 {
			_, _ = fmt.Fprintln(c.errorOutput, "Password must not be empty.")
			continue
		}
		_, _ = fmt.Fprint(c.errorOutput, "Confirm password: ")
		second, err := c.readPassword()
		_, _ = fmt.Fprintln(c.errorOutput)
		if err != nil {
			return "", fmt.Errorf("failed to read password: %w", err)
		}
		if string(first) != string(second) {
			_, _ = fmt.Fprintln(c.errorOutput, "Passwords do not match, try again.")
			continue
		}
		return string(first), nil
	}
	return "", errors.New("no password set after 3 attempts")
}

func (c *tenantsCommand) list(baseDir string, store *pt.Store) error {
	tenants, err := store.List()
	if err != nil {
		return err
	}
	running := camundaRunning(baseDir)
	active := activeTenantIDs(baseDir)
	tw := tabwriter.NewWriter(c.output, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(tw, "TENANT\tSTATUS\tLOGIN\tCONNECTORS\tOPERATE\tORCHESTRATION API")
	d := pt.EndpointsFor(pt.DefaultID, "http", c.port)
	_, _ = fmt.Fprintf(tw, "%s\t%s\t%s\t%s\t%s\t%s\n", pt.DefaultID, statusLabel(running, true), "start login", "yes", d.Operate, d.REST)
	listed := map[string]bool{}
	for _, t := range tenants {
		listed[t.ID] = true
		e := pt.EndpointsFor(t.ID, "http", c.port)
		login := "start login"
		if t.Username != "" {
			login = t.Username
		}
		conn := "yes"
		if t.NoConnectors {
			conn = "no"
		}
		_, _ = fmt.Fprintf(tw, "%s\t%s\t%s\t%s\t%s\t%s\n", t.ID, statusLabel(running, active[t.ID]), login, conn, e.Operate, e.REST)
	}
	var removed []string
	for id := range active {
		if !listed[id] && running {
			removed = append(removed, id)
		}
	}
	sort.Strings(removed)
	for _, id := range removed {
		_, _ = fmt.Fprintf(tw, "%s\t%s\t-\t-\t-\t-\n", id, "removed (restart to apply)")
	}
	if err := tw.Flush(); err != nil {
		return err
	}
	if len(tenants) == 0 {
		_, _ = fmt.Fprintln(c.output, "\nNo extra physical tenants yet. Add one with `c8run tenants add <id>`.")
	}
	return nil
}

func statusLabel(running, active bool) string {
	switch {
	case !running:
		return "stopped"
	case active:
		return "active"
	default:
		return "pending restart"
	}
}

func (c *tenantsCommand) remove(baseDir string, store *pt.Store, args []string) error {
	var ids []string
	confirmed := false
	for _, arg := range args {
		switch {
		case arg == "--yes" || arg == "-y":
			confirmed = true
		case strings.HasPrefix(arg, "-"):
			return fmt.Errorf("unsupported option: %s", arg)
		default:
			ids = append(ids, arg)
		}
	}
	if len(ids) == 0 {
		return errors.New("usage: c8run tenants remove <id> [id...] [--yes]")
	}
	for _, id := range ids {
		if id == pt.DefaultID {
			return errors.New("the default physical tenant cannot be removed")
		}
	}
	if !c.confirm(confirmed, fmt.Sprintf("Remove physical tenant(s) %s?", strings.Join(ids, ", "))) {
		_, _ = fmt.Fprintln(c.output, "No tenants removed.")
		return nil
	}
	if err := withoutInterrupts(func() error { return store.Remove(ids) }); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(c.output, "Removed physical tenant(s): %s.\n", strings.Join(ids, ", "))
	_, _ = fmt.Fprintln(c.output, "Their data is kept in secondary storage under the tenant's prefix. Adding the same ID again restores it, including the users created in that tenant.")
	c.printRestartHint(baseDir, "stop", 0)
	return nil
}

func (c *tenantsCommand) reset(baseDir string, store *pt.Store, args []string) error {
	confirmed := len(args) == 1 && (args[0] == "--yes" || args[0] == "-y")
	if len(args) > 0 && !confirmed {
		return errors.New("usage: c8run tenants reset [--yes]")
	}
	if !c.confirm(confirmed, "Remove all physical tenants and their stored logins?") {
		_, _ = fmt.Fprintln(c.output, "No tenants removed.")
		return nil
	}
	if err := store.Reset(); err != nil {
		return err
	}
	_, _ = fmt.Fprintln(c.output, "All physical tenants removed. Only the default tenant will start.")
	c.printRestartHint(baseDir, "stop", 0)
	return nil
}

func (c *tenantsCommand) confirm(confirmed bool, question string) bool {
	if confirmed {
		return true
	}
	if !c.isTerminal() {
		_, _ = fmt.Fprintln(c.errorOutput, "Non-interactive use requires --yes.")
		return false
	}
	_, _ = fmt.Fprintf(c.errorOutput, "%s [y/N]: ", question)
	answer, err := bufio.NewReader(c.input).ReadString('\n')
	if err != nil && !errors.Is(err, io.EOF) {
		return false
	}
	answer = strings.TrimSpace(answer)
	return strings.EqualFold(answer, "y") || strings.EqualFold(answer, "yes")
}

func (c *tenantsCommand) printRestartHint(baseDir, verb string, count int) {
	if camundaRunning(baseDir) {
		_, _ = fmt.Fprintf(c.errorOutput, "Camunda is running; the change takes effect on the next start. Run `./c8run stop && ./c8run start` to apply it now.\n")
		return
	}
	if verb == "start" {
		subject := "this tenant"
		if count > 1 {
			subject = "these tenants"
		}
		_, _ = fmt.Fprintf(c.output, "Run `./c8run start` to start Camunda with %s.\n", subject)
	}
}

func camundaRunning(baseDir string) bool {
	_, err := os.Stat(filepath.Join(baseDir, "camunda.process"))
	return err == nil
}

// activeTenantIDs reads the tenants the running Camunda was started with.
func activeTenantIDs(baseDir string) map[string]bool {
	ids := map[string]bool{}
	content, err := os.ReadFile(filepath.Join(baseDir, "configuration", pt.GeneratedConfigName))
	if err != nil {
		return ids
	}
	var root struct {
		Camunda struct {
			PhysicalTenants map[string]any `yaml:"physical-tenants"`
		} `yaml:"camunda"`
	}
	if yaml.Unmarshal(content, &root) == nil {
		for id := range root.Camunda.PhysicalTenants {
			ids[id] = true
		}
	}
	return ids
}

const tenantsHelp = `Usage:
  c8run tenants add <id> [id...] [--username <name>] [--password-stdin] [--no-connectors]
  c8run tenants list
  c8run tenants remove <id> [id...] [--yes]
  c8run tenants reset [--yes]
  c8run tenants path

Physical tenants are fully isolated engines inside one c8run: each has its own partitions,
its own data in secondary storage, its own users, and its own Operate, Tasklist and API at
http://localhost:8080/physical-tenants/<id>/. The "default" tenant always exists and is
served at the unprefixed URLs.

Tenant IDs use lowercase letters and digits only (max 64), e.g. "sales" or "team2".
Tenants are saved for the current OS user and applied on every ` + "`c8run start`" + `.
Use ` + "`c8run start --physical-tenants a,b`" + ` to run with tenants for one run without saving them.

Aliases: c8run pt, c8run physical-tenants.
C8RUN_TENANTS_FILE selects another tenants file; relative paths use the current directory.
C8RUN_TENANTS_MODE defaults to local; external disables these commands so camunda.physical-tenants
in your --config file is the only source.
Requires Camunda 8.10 or newer.
`

var tenantsCommandHelp = map[string]string{
	"add":    "Usage: c8run tenants add <id> [id...] [--username <name>] [--password-stdin] [--no-connectors]\nAdds physical tenants for the next start. Without --username, each tenant gets the same login as `c8run start`.\nWith --username, you are prompted for a password (or pass it on stdin with --password-stdin).\nEach tenant gets its own connectors runtime unless --no-connectors is set.\n",
	"list":   "Usage: c8run tenants list\nShows every physical tenant with its status, login, connectors and URLs.\n",
	"ls":     "Usage: c8run tenants list\n",
	"remove": "Usage: c8run tenants remove <id> [id...] [--yes]\nRemoves tenants from the next start. Data stays in secondary storage; adding the ID again restores it.\n",
	"rm":     "Usage: c8run tenants remove <id> [id...] [--yes]\n",
	"reset":  "Usage: c8run tenants reset [--yes]\nRemoves all physical tenants and stored tenant logins.\n",
	"path":   "Usage: c8run tenants path\nShows where physical tenants are saved.\n",
}
