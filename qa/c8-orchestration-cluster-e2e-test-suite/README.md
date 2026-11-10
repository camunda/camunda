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
CAMUNDA_TASKLIST_V2_MODE_ENABLED=false
```

---

### 4. Start Local Camunda 8 Self-Managed Environment

For running tests locally, ensure you have an active instance. To set it up:

1. Open a terminal in the `config` folder inside the `c8-orchestration-cluster-e2e-test-suite` directory.
2. Run:

**For Tasklist V1 mode:**

```bash
CAMUNDA_TASKLIST_V2_MODE_ENABLED=false DATABASE=elasticsearch docker compose up -d camunda
```

**For Tasklist V2 mode:**

```bash
DATABASE=elasticsearch docker compose up -d camunda
```

This launches Tasklist and Operate with Elasticsearch as the backing database.
🛠️ Ensure the ports in your .env match those used in this setup (e.g., 8080, 8081, 8089).

## Running Tests Locally

### Headless Mode

To run the tests without a graphical user interface:

```bash
npm run test:local
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
  - `pages/`: V2 page objects (default)
  - `pages/v1/`: Tasklist V1-specific page objects
- **Test specs**: `qa/c8-orchestration-cluster-e2e-test-suite/tests`
  - `tests/tasklist/`: V2 Tasklist tests
  - `tests/tasklist/v1/`: V1 Tasklist tests
  - `tests/common-flows/`: V2 common flow tests
  - `tests/common-flows/v1/`: V1 common flow tests
  - `tests/operate/`: Operate tests
  - `tests/identity/`: Identity tests
  - `tests/api/`: API tests
- **Utilities/fixtures**: `qa/c8-orchestration-cluster-e2e-test-suite/utils`
- **Test data**: `qa/c8-orchestration-cluster-e2e-test-suite/resources`

### Test Mode Separation

Starting with Camunda 8.8, Tasklist V2 is the default mode. The test suite reflects this:

- **V2 Mode (Default)**: All tests run against Tasklist V2 unless specified otherwise
- **V1 Mode (Explicit)**: V1 tests require explicit project specification
- Tests are organized into mode-specific directories for clear separation
- Page objects are separated by mode to eliminate compatibility overhead

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
- Reviewer must be the someone from the Test Automation Team and a product team developer

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
