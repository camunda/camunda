# Orchestration Cluster Smoke Tests

This package contains a smoke test suite for orchestration cluster applications.
It focuses on testing critical user journeys through Operate and Tasklist.

## Getting Started

1. Ensure that you have Node.js installed with the version defined in the
   `.nvmrc` file.
2. Run `npm i` to install all required dependencies.
3. If you are using VS Code, ensure your editor uses the local TypeScript SDK.

### Static Code Analysis

Run `npm run lint` to execute static checks on the test suite. These include
type-checks, linting, and formatting checks.

If the step reports formatting issues, run `npm run format` to format all files
in the package.

### Running Tests

1. Run `npm run cluster:up` to start an Orchestration Cluster with
   ElasticSearch.
2. Run `npm run cluster:wait` to wait until Operate and Tasklist are ready.
3. Run `npm run test` to execute the smoke test suite.
4. Run `npm run cluster:down` to remove the Orchestration Cluster.

### Developing Tests

Run `npm run test:ui` instead to open Playwright's UI mode. This command reduces
the iteration cycle when making changes and adding new tests.
