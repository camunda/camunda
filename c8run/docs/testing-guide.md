# Testing Guide

## General Principles

- Test observable behavior, not implementation details.
- Keep tests small and focused on the package under test.

## Platform Coverage

Changes under `internal/unix/` or `internal/windows/` should include matching tests, or include an explicit comment explaining why only one platform is affected. Shared behavior should be tested at the shared package level above those directories.

## Process Lifecycle

For process lifecycle changes, cover:

- PID file creation and cleanup
- `.lock` file creation and cleanup
- Stale process detection and handling
- Foreground shutdown (Ctrl+C / signal handling)
- `./c8run stop` flow
- Windows child process tracking where applicable

## Startup and Connectors

For startup changes, cover both Camunda and Connectors when behavior is shared or coordinated. Verify health check behavior and the startup URL opening flow.

## Packaging

For packaging changes:

- Verify the archive file list — the expected set of files must be present
- Verify that runtime artifacts (PID files, `log/`, `camunda-data/`, `jre/`, extracted dirs, built binaries) are not included in the archive

## E2E Tests

API and Playwright-based E2E tests live in `e2e_tests/`. These are run by C8Run CI. Run locally only against a fully started C8Run instance.
