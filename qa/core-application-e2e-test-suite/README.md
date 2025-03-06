# Camunda Core Application E2e Test Suite

This repository contains the end-to-end test suite for Camunda's core components. Follow the steps below to get started and run the tests locally.

---

## Getting Started

### 1. Clone the Repository

Clone the repository to your local machine:

```bash
git clone https://github.com/camunda/camunda.git
```

### 2. Install Dependencies

Navigate to the `core-application-e2e-test-suite` directory and install the required dependencies:

```bash
npm install
npx playwright install
```

### 3. Configure Environmental Variables

Create a `.env` file inside `core-application-e2e-test-suite`. Your `.env` file should look similar to this:

```bash
LOCAL_TEST=true
MINOR_VERSION=version_to_test
CORE_APPLICATION_TASKLIST_URL=http://localhost:8080
CORE_APPLICATION_OPERATE_URL=http://localhost:8081
```

---

## Running Tests Locally

### Headless Mode

To run the tests without a graphical user interface (headless mode), execute:

```bash
npm run test:local
```

### Interactive Mode (UI)

To run the tests interactively (with the UI), use the following command:

```bash
npx playwright test --ui
```

---

## Additional Notes for Local Testing

### C8 SM Requirements

For testing with C8 SM, ensure you have an active SM instance. To set this up:

1. Open a terminal in the `config` folder inside the `tasklist` directory.
2. Run the following command to start the necessary services using Docker Compose:

   ```bash
   DATABASE=elasticsearch docker compose up -d tasklist operate
   ```
