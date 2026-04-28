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
  - [Running the On-Demand Workflow](#running-the-on-demand-workflow)
  - [Automated Nightly Test Runs](#automated-nightly-test-runs)
  - [CI Test Results](#ci-test-results)
- [Contributing](#contributing)
  - [Project Board](#project-board)
  - [TestRail Integration](#testrail-integration)
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

### Running the On-Demand Workflow

- `c8-orchestration-cluster-e2e-tests-on-demand.yml`: Manually triggered for ad hoc testing

1. Go to [C8 Orchestration Cluster E2E Tests On Demand](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-e2e-tests-on-demand.yml)
2. Click **"Run workflow"**
3. Choose the desired branch (e.g., `main`, `stable/8.6`, `stable/8.7`, `stable/8.8`)
4. Click **"Run workflow"**

---

### Automated Nightly Test Runs

- `c8-orchestration-cluster-e2e-tests-nightly.yml`: Runs nightly across all monorepo versions
  Nightly tests ensure continuous stability

- All nightly test runs can be accessed from [C8 Orchestration Cluster E2E Tests Nightly](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-e2e-tests-nightly.yml)

- Results posted to Slack channel `#c8-orchestration-cluster-e2e-test-results` with TestRail links

- Failures are reviewed by the `qa-automated-release-manager`

---

### CI Test Results

- Results available under the **Actions** tab for each workflow run
- View logs, traces, and download artifacts
- Test runs are published to [TestRail](https://camunda.testrail.com/index.php?/runs/overview/33) for visibility

---

## Contributing

We welcome contributions! To contribute:

- Follow the POM pattern
- Test cases must involve at least one core component
- Reviewer must be someone from the Test Automation Team and a product team developer

> [!IMPORTANT]
> **Do not request a review until all relevant tests have been run and pass.**
> Before assigning reviewers to your PR:
> 1. Run the [on-demand workflow](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-e2e-tests-on-demand.yml) against your branch.
> 2. Wait for the workflow to complete.
> 3. Verify all tests pass (or document any known failures with a clear explanation in the PR description).
> 4. Only then request a review.
>
> PRs assigned for review without a completed test run will be returned to the author.

### Project Board

We track progress via the [C8 Orchestration Cluster E2E Tests Project Board](https://github.com/orgs/camunda/projects/178/views/1):

- View test case statuses
- See who's assigned to what
- Link your PR to the board if needed

---

### TestRail Integration

If a PR modifies any test or page file, link the corresponding [TestRail test case suite](https://camunda.testrail.com/index.php?/suites/view/17050) in the PR description.

Need TestRail access? Contact the QA team.

---

### How to Request a New Test Case

If you want to suggest a new test case without submitting code:

1. Go to the [Issues tab](https://github.com/camunda/camunda/issues) inside [camunda Repo](https://github.com/camunda/camunda).
2. Click **"New Issue"** and select **"C8 Orchestration Cluster – Automated E2E Test Request"**
3. Fill in all required fields

---

Thank you for using the C8 Orchestration Cluster End-to-End Test Suite.
Happy testing! 🚀 For help, reach out to the DRI.
