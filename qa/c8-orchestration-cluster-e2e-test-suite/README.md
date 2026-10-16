# Camunda C8 Orchestration Cluster E2E Test Suite – Developer Guide

This repository contains the end-to-end test suite for Camunda's C8 orchestration cluster, built with [Playwright](https://playwright.dev/). It automates UI and API testing across orchestration cluster apps.
Follow the steps below to get started and run the tests locally.

---

## 📚 Table of Contents

- [Getting Started](#getting-started)
  - [1. Clone the Repository](#1-clone-the-repository)
  - [2. Install Dependencies](#2-install-dependencies)
  - [3. Configure Environment Variables](#3-configure-environment-variables)
  - [4. Start Local Camunda 8 Self-Managed Environment](#4-start-local-camunda-8-self-managed-environment)
- [Running Tests Locally](#running-tests-locally)
  - [Headless Mode](#headless-mode)
  - [Interactive Mode (UI)](#interactive-mode-ui)
  - [Viewing Local Test Results](#viewing-local-test-results)
- [Additional Notes for Local Testing](#additional-notes-for-local-testing)
  - [C8 SM Requirements (Self-Managed)](#c8-sm-requirements-self-managed)
- [Test Suite Structure (Page Object Model)](#test-suite-structure-page-object-model)
- [GitHub Actions for Testing](#github-actions-for-testing)
  - [Automatic PR Check](#automatic-pr-check)
  - [Running the On-Demand Workflow](#running-the-on-demand-workflow)
  - [Automated Nightly Test Runs](#automated-nightly-test-runs)
  - [CI Test Results](#ci-test-results)
- [Contributing](#contributing)
  - [Project Board](#project-board)
  - [How to Request a New Test Case](#how-to-request-a-new-test-case)

---

## Getting Started

### 1. Clone the Repository

Clone the repository to your local machine:

```bash
git clone https://github.com/camunda/camunda.git
```

---

### 2. Install Dependencies

Navigate to the `c8-orchestration-cluster-e2e-test-suite` directory and install the required dependencies:

```bash
npm install
npx playwright install
```

---

### 3. Configure Environment Variables

Create a `.env` file inside `c8-orchestration-cluster-e2e-test-suite`.
This file configures test parameters, including application URLs and credentials.
**Note**: Do not commit the `.env` file to GitHub to avoid exposing sensitive information.

Example:

```env
LOCAL_TEST=true
CORE_APPLICATION_URL=http://localhost:8080
CAMUNDA_AUTH_STRATEGY=BASIC
CAMUNDA_BASIC_AUTH_USERNAME=demo
CAMUNDA_BASIC_AUTH_PASSWORD=demo
ZEEBE_REST_ADDRESS=http://localhost:8080
DATABASE_CONTAINER=<Service name from db/docker-compose.yml>
```

---

### 4. Start Local Camunda 8 Self-Managed Environment

For running tests locally, ensure you have an active instance. To set it up:

#### General flow for starting the environment:

1. Open a terminal in the `config` folder inside the `c8-orchestration-cluster-e2e-test-suite` directory.
2. Run:

```bash
DATABASE=elasticsearch docker compose up -d camunda
```

This launches Tasklist and Operate with Elasticsearch as the backing database.
🛠️ Ensure the ports in your .env match those used in this setup (e.g., 8080, 8081, 8089).

#### RDBMS flow for starting the environment:

1. Build distribution locally from the desired branch:

```bash
./mvnw install -Dquickly -T1C -PskipFrontendBuild
```

2. Untar the distribution:

```bash
cd camunda/dist/target
tar -xzf camunda-orchestration-cluster-*.tar.gz
```

3. Configure connection of the applications to the database by providing the [application.yaml](qa/c8-orchestration-cluster-e2e-test-suite/config/application.yaml) (this example with oracle db) file
   with the correct database connection details (e.g., URL, username, password). Alternatively define appropriate environment variables in the run command or your docker environment.:

```bash
cp qa/c8-orchestration-cluster-e2e-test-suite/config/application.yaml \
   dist/target/camunda-zeebe-8.10.0-SNAPSHOT/config/
```

4. Install JDBC driver for the chosen database in the `lib` folder of the distribution (e.g., `ojdbc11.jar` for Oracle).

```bash
curl -L -o dist/target/camunda-zeebe-8.10.0-SNAPSHOT/lib/ojdbc11.jar \
    https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.3.0.23.09/ojdbc11-23.3.0.23.09.jar
```

5. Start the database container:

```bash
docker compose -f db/docker-compose.yml up -d --wait <Service name from db/docker-compose.yml, e.g. oracle>
```

6. Start the application:

```bash
cd dist/target/camunda-zeebe-8.10.0-SNAPSHOT
export SPRING_PROFILES_ACTIVE="broker,consolidated-auth,admin,operate,tasklist"
export ZEEBE_CLOCK_CONTROLLED="true"
export CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI="false"
export CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED="true"
export CAMUNDA_SECURITY_AUTHENTICATION_METHOD="BASIC"
export CAMUNDA_SECURITY_MULTITENANCY_CHECKSENABLED="false"
export CAMUNDA_SECURITY_INITIALIZATION_USERS_0_USERNAME="demo"
export CAMUNDA_SECURITY_INITIALIZATION_USERS_0_PASSWORD="demo"
export CAMUNDA_SECURITY_INITIALIZATION_USERS_0_NAME="Demo"
export CAMUNDA_SECURITY_INITIALIZATION_USERS_0_EMAIL="demo@example.com"
export CAMUNDA_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_0="demo"
export CAMUNDA_SECURITY_INITIALIZATION_USERS_1_USERNAME="lisa"
export CAMUNDA_SECURITY_INITIALIZATION_USERS_1_PASSWORD="lisa"
export CAMUNDA_SECURITY_INITIALIZATION_USERS_1_NAME="lisa"
export CAMUNDA_SECURITY_INITIALIZATION_USERS_1_EMAIL="lisa@example.com"
export CAMUNDA_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_1="lisa"
export CAMUNDA_DATA_AUDITLOG_ENABLED="true"
export CAMUNDA_DATA_AUDITLOG_USER_CATEGORIES_0="ADMIN"
export CAMUNDA_DATA_AUDITLOG_USER_CATEGORIES_1="DEPLOYED_RESOURCES"
export CAMUNDA_DATA_AUDITLOG_USER_CATEGORIES_2="USER_TASKS"
export CAMUNDA_DATA_AUDITLOG_USER_EXCLUDES_0="VARIABLE"
export CAMUNDA_DATA_AUDITLOG_USER_EXCLUDES_1="BATCH"
export CAMUNDA_DATA_AUDITLOG_CLIENT_CATEGORIES_0="ADMIN"
export CAMUNDA_DATA_AUDITLOG_CLIENT_EXCLUDES_0="PROCESS_INSTANCE"
./bin/camunda
```

## Running Tests Locally

### Headless Mode

To run the tests without a graphical user interface:

```bash
npm run test:local
```

To run API V2 tests against RDBMS setup, use:

```bash
DATABASE=RDBMS npm run test -- --project=api-tests
```

### Interactive Mode (UI)

To run the tests with the UI:

```bash
npx playwright test --ui
```

### Viewing Local Test Results

After running tests locally, you can view the report:

```bash
npx playwright show-report html-report
```

Reports and artifacts:

- `qa/c8-orchestration-cluster-e2e-test-suite/html-report`: Latest HTML report
- `qa/c8-orchestration-cluster-e2e-test-suite/test-results/`: Traces and screenshots

---

## Test Suite Structure (Page Object Model)

This test suite follows the **Page Object Model (POM)** pattern for reusability and maintainability.

### Directory Structure

- **Page objects**: `qa/c8-orchestration-cluster-e2e-test-suite/pages`
- **Test specs**: `qa/c8-orchestration-cluster-e2e-test-suite/tests`
  - `tests/tasklist/`: Tasklist tests
  - `tests/common-flows/`: Common flow tests
  - `tests/operate/`: Operate tests
  - `tests/identity/`: Identity tests
  - `tests/api/`: API tests
- **Utilities/fixtures**: `qa/c8-orchestration-cluster-e2e-test-suite/utils`
- **Test data**: `qa/c8-orchestration-cluster-e2e-test-suite/resources`

---

## GitHub Actions for Testing

### Automatic PR Check

When a pull request changes files under `qa/c8-orchestration-cluster-e2e-test-suite/`,
the suite runs automatically as a required check (no manual trigger needed). It mirrors
the on-demand matrix (API, H2/RDBMS API and E2E) for the PR's base branch.

The check is **scoped to the tests you changed**: the full suite runs, but the check
blocks the merge only when a spec your PR **added or modified** fails. Failures in
untouched tests are reported in the uploaded report but do not block — keeping the
historically flaky parts of the suite from blocking unrelated changes.

Run the on-demand workflow below when you need the full suite to gate (for example a
change to a shared page object, fixture or config that can break untouched tests without
changing any spec file), or for a pull request from a fork, which the automatic check
skips.

> **Product code + test in the same PR:** when a suite PR **also** changes product code baked
> into the Camunda image (any change that makes `build-distball` run — backend, frontend, or a
> Dockerfile change), the ES API and E2E (browser) jobs build the image from your PR branch and
> run against it, so they validate your test changes against your own product change. A
> **suite-only** PR keeps running against the published `camunda/camunda:SNAPSHOT` image, which is
> the correct baseline since there is no product change to build. The H2/RDBMS job always builds
> from source regardless.
>
> Remaining gap: only the `camunda/camunda` image is branch-built — `camunda/optimize` still
> comes from its published `SNAPSHOT`, so a PR that changes Optimize product code plus a suite
> spec that exercises Optimize should still be validated with a manual on-demand run against your
> branch.

### Running the On-Demand Workflow

- `c8-orchestration-cluster-e2e-tests-on-demand.yml`: Manually triggered for ad hoc testing

1. Go to [C8 Orchestration Cluster E2E Tests On Demand](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-e2e-tests-on-demand.yml)
2. Click **"Run workflow"**
3. Choose the desired branch (e.g., `main`, `stable/8.6`, `stable/8.7`, `stable/8.8`)
4. Click **"Run workflow"**

---

### Automated Nightly Test Runs

- `c8-orchestration-cluster-nightly-<version>-<type>.yml`: Runs nightly for `main` and each supported `stable/*` branch, as UI (`e2e`) and API (`api-es`, `api-rdbms`) runs
- Results posted to Slack channel `#c8-orchestration-cluster-e2e-test-results`
- Failures are triaged every weekday by `C8 Orchestration Cluster Nightly Triage`, which dispatches a fix agent per failing version
- See the [nightly runbook](docs/nightly-runbook.md) for the schedule, test ownership, and how to fix, skip, or escalate a failure

---

### CI Test Results

- Results available under the **Actions** tab for each workflow run
- View logs, traces, and download artifacts

---

## Contributing

We welcome contributions! To contribute:

- Follow the POM pattern
- Test cases must involve at least one core component
- Reviewers must include someone from the Test Automation Team and a product team developer

> [!IMPORTANT]
> **Your changed tests must pass before review.**
> A PR that changes the suite runs the [Automatic PR Check](#automatic-pr-check), which blocks the
> merge on the specs you added or modified. Do not request a review while that check is red for one
> of your own tests.
>
> The automatic check does **not** gate on untouched tests, and it does not run on fork PRs. When
> your change can break tests you did not edit (a shared page object, fixture or config), or your PR
> is from a fork, also run the [on-demand workflow](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-e2e-tests-on-demand.yml)
> against your branch, wait for it to complete, and document any failures in the PR description,
> confirming they are pre-existing and not regressions caused by your changes.

### Project Board

We track progress via the [C8 Orchestration Cluster E2E Tests Project Board](https://github.com/orgs/camunda/projects/178/views/1):

- View test case statuses
- See who's assigned to what
- Link your PR to the board if needed

---

### How to Request a New Test Case

If you want to suggest a new test case without submitting code:

1. Go to the [Issues tab](https://github.com/camunda/camunda/issues) inside [camunda Repo](https://github.com/camunda/camunda).
2. Click **"New Issue"** and select **"C8 Orchestration Cluster – Automated E2E Test Request"**
3. Fill in all required fields

---

Thank you for using the C8 Orchestration Cluster End-to-End Test Suite.
Happy testing! 🚀 For help, reach out to the DRI.
