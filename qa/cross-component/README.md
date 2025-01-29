# C8 Cross-Component End-to-End Test Suite

## Overview

The C8 Cross-Component End-to-End Test Suite represents an extensive collection of end-to-end tests meticulously tailored for Camunda C8, encompassing the entire product stack. This specialized suite is dedicated to test scenarios that traverse multiple C8 components, harnessing the power of Playwright with TypeScript. It adheres to the Page Object Model design pattern, ensuring effortless test creation and seamless maintenance. The focus of the test suite is to align with the C8 persona profiles and their user flows as well as the most important cross-component user flows for the product stack determined by each product team. The persona profiles that will be focused on can be observed in this [link](https://confluence.camunda.com/display/HAN/Personas)

## Supported Testing Platforms

### C8 SaaS, C8 Self-Managed (SM) with Helm, and C8 Run

This test suite supports testing across three primary platforms:

1. **C8 SaaS**
2. **C8 Self-Managed with Helm Chart**
3. **C8 Run**

## Supported Cluster Versions for SaaS

For the Camunda SaaS offering, the test suite supports following cluster versions:

- 8.3.X
- 8.4.X
- 8.5.X
- 8.6.X
- 8.7.X

**By default, the test suite is configured to run tests on the latest stable patch generations and the latest snapshot versions.** If you wish to run tests on a different generation, you can adjust the configurations in the `.github/c8_versions.json` file.

## Supported Versions for SM with Helm

For the Camunda SM Helm offering, the test suite supports following versions:

- 8.3.X
- 8.4.X
- 8.5.X
- 8.6.X
- 8.7.X

## Supported Versions for C8Run

For the Camunda C8Run offering, the test suite supports following versions:

- 8.6.X
- 8.7.X

### GitHub Actions for Testing

GitHub Actions can be used to facilitate testing across various cluster versions and components:

#### SaaS

For SaaS, a test run can be manually triggered for specific minor versions and their associated cluster generation. This can be done through the [Playwright SaaS Manual GitHub Action](https://github.com/camunda/c8-cross-component-e2e-tests/actions/workflows/playwright_saas_manual%20.yml).

#### SM with Helm

For SM using Helm, a test run can manually trigger by specifying any component version as an input. This can be done through the [Playwright SM Manual GitHub Action](https://github.com/camunda/c8-cross-component-e2e-tests/actions/workflows/playwright_sm_manual.yml).

#### C8Run

For C8Run, a test run can be manually triggered for specific minor versions, for each supported Operating System. This can be done through the following workflows:

- [Manual c8Run Test Run with Linux GitHub Action](https://github.com/camunda/c8-cross-component-e2e-tests/actions/workflows/playwright_c8Run_tests_manual_linux.yml)
- [Manual c8Run Test Run with macOS GitHub Action](https://github.com/camunda/c8-cross-component-e2e-tests/actions/workflows/playwright_c8Run_tests_manual_mac.yml)
- [Manual c8Run Test Run with Windows GitHub Action](https://github.com/camunda/c8-cross-component-e2e-tests/actions/workflows/playwright_c8Run_tests_manual_windows.yml)


## Prerequisites

1. Before running the test suite, make sure you have the following prerequisites installed:

- Node.js (with npm, >= v14)

2. Request the setup of an organization identical to `Automation QA` (Org with ID: `3626cf46-8f97-4711-81b3-ecfc341348c2`) on INT

- Ensure you are set up as the Owner of this organization.
- Name the organization as `Automation QA - Your_Name`.

3. In the organization `Automation QA - Your_Name`, invite the following users:

   - Alias of `C8_USERNAME`
   - Alias of `C8_USERNAME_TEST`
   - Alias of `C8_USERNAME_TEST_2`

4. Retrieve the passwords for the Gmail accounts from Keeper (Shared - Engineering All -> C8 Cross Component E2E Test Suite Credentials).

5. In the respective emails, sign up and accept the invitations. Make sure to align the passwords with `C8_PASSWORD` and `C8_PASSWORD_TEST` from Keeper

## Getting Started

1. Clone the repository:

```bash
git clone https://github.com/camunda/c8-cross-component-e2e-tests.git
```

2. Install the dependencies:

```bash
npm install
npx playwright install
```

3. Add Enviornmental Variables:

Create a .env file in the root of the project. Add the credentials found in Keeper (Shared - Engineering All -> C8 Cross Component E2E Test Suite Credentials) to this file. The .env file should look like this:

```bash
C8_USERNAME=created_alias_username
C8_PASSWORD=camunda_c8_password_from_keeper
C8_USERNAME_TEST=created_alias_username_test
C8_PASSWORD_TEST=camunda_c8_password_test_from_keeper
C8_USERNAME_TEST_2=created_alias_username_test_2
C8_PASSWORD_TEST_2=camunda_c8_password_test_2_from_keeper
C8_INVALID_USERNAME=invalid_camunda_c8_username_from_keeper
LOCAL_TEST=true
CLUSTER_VERSION=your_cluster_version
ORG_ID=your_created_organization_id
ORG_NAME=your_created_organization_name
MAIL_SLURP_API_KEY=mailslurp_api_key
REGION_SELECTION=true
```

If you intend to run the test suite on the Self-Managed Environment also add the following line to your .env file:

```bash
PLAYWRIGHT_BASE_URL=sm_base_url_from_keeper
DISTRO_QA_E2E_TESTS_IDENTITY_FIRSTUSER_PASSWORD=sm_password_from_keeper
DISTRO_QA_E2E_TESTS_IDENTITY_SECONDUSER_PASSWORD=sm_password_from_keeper
DISTRO_QA_E2E_TESTS_IDENTITY_THIRDUSER_PASSWORD=sm_password_from_keeper
DISTRO_QA_E2E_TESTS_KEYCLOAK_CLIENTS_SECRET=client_secret_from_keeper
CLUSTER_ENDPOINT=cluster_endpoint_from_keeper
OAUTH_URL=oauth_url_from_keeper
OPERATE_BASE_URL=operate_base_url_from_keeper
TASKLIST_BASE_URL=tasklist_base_url_from_keeper
ZEEBE_REST_URL=zeebe_rest_url_from_keeper
```

Note: Make sure to keep the .env file private and do not commit it to version control to keep your credentials secure.

## Running Tests Locally

1. Run Tests in Headless Mode

To run the test suite in headless mode (without a graphical user interface), use the following command:

```bash
npm run test:local
```


2. Run Tests with UI (Interactive Mode)

To run the test suite with the UI (interactive mode), append --ui at the end of the npx playwright test command (note the test 'Create Cluster' must be run first):

```bash
npx playwright test --ui
```

### Additional Notes for Local Testing

#### C8 SaaS
For a full test run, no additional configuration is required. However, if you are running a single test, ensure you have a healthy and active cluster.

#### C8 Self-Managed (SM) with Helm
To test locally on the SM Helm environment, you need an active SM instance. You can create one through any of the following GitHub Actions workflows:

- [Basic Helm Chart](https://github.com/camunda/c8-cross-component-e2e-tests/actions/workflows/playwright_sm_manual_no_tests.yml)
- [Helm Chart With RBA Enabled](https://github.com/camunda/c8-cross-component-e2e-tests/actions/workflows/playwright_sm_manual_rba_no_tests.yml)
- [Helm Chart With Multi Tenancy Enabled](https://github.com/camunda/c8-cross-component-e2e-tests/actions/workflows/playwright_sm_manual_mt_no_tests.yml)

Once the SM instance is created, copy the generated test URL and input it into your `.env` file under the `PLAYWRIGHT_BASE_URL` variable.

#### C8 Run
To test locally against C8 Run, you must set up a local instance of C8 Run. Please follow the instructions provided in the [C8 Run README.md](https://github.com/camunda/camunda/blob/main/c8run/README.md). By default, the BASE_URL is localhost:8080, however if this is configured as something differently, tests can be run against this by inputting the new BASE_URL into your `.env` file under the `PLAYWRIGHT_BASE_URL` variable.

## Automated Nightly Test Runs

The test suite is configured to run automatically every night. The results of these nightly test runs are published on TestRail. If any test fails during these automated runs, the Designated Responsible Individual (DRI) of the project is notified via email for immediate attention and resolution. On a nightly basis, the test suite is configured to run the following tests:

- **C8 SaaS**: Tests are run against a SNAPSHOT generation cluster.
- **C8 SM with Helm**: Tests are run against the alpha branch of the [camunda-platform-helm repository](https://github.com/camunda/camunda-platform-helm), using SNAPSHOT images for each component.
- **C8 Run**: Tests are run against the main branch of the [camunda/camunda repository](https://github.com/camunda/camunda).

## Test Suite Structure

The test suite is organized using the Page Object Model design pattern, where each test file contains test scenarios related to a specific component or feature of the Camunda C8 product stack. Additionally, it leverages separate page objects for each web page or component being tested.

You can find the page objects in the pages directory, and the test files in the tests directory.

For example:

Page files:

LoginPage.ts - Page object for the login functionality.

Test files:

login.spec.ts - Tests related to the login functionality using the LoginPage object.

Feel free to add more test files or page objects to expand the test coverage based on your specific requirements.

## Viewing Test Results

### Locally:

The test results are presented as a comprehensive list in the terminal when all tests pass. Additionally, within the project's test-results folder, you can find the test results accompanied by images. In the event of a test failure, a video capturing the issue is provided. Moreover, if any test encounters issues or instability, an HTML report automatically opens, facilitating in-depth analysis.

### On the CI:

Within the CI environment, test results are accessible through TestRail, located in the C8 Project folder under Test Runs. These reports not only include detailed test results but also feature screenshots. In case of a test failure, you will find the stacktrace and an accompanying test video attached. Furthermore, the CI environment offers extensive tools for in-depth analysis of test results and reports.

## Contributing

Contributions to the C8 Cross-Component End-to-End Test Suite are welcome! If you find any issues or want to add new test cases, please submit a pull request or open an issue on GitHub.

To contribute, please follow these guidelines:

- Anyone can contribute.
- The reviewer of the pull request must be the DRI of the project and a developer from the product team.
- Test cases must adhere to the Page Object Model design pattern.
- Test cases must involve multiple C8 components.

### TestRail Integration

To ensure our test case documentation on TestRail remains up-to-date, if a PR contains updates to any test file or page file, the associated TestRail test case **must** be linked in the PR description. 

If you do not have access to TestRail, please contact the DRI of the repository to request access.


Thank you for using the C8 Cross-Component End-to-End Test Suite for Camunda C8 full product stack e2e testing. Happy testing! If you have any questions or need assistance, feel free to reach out to the maintainers for support.
