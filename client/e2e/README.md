# E2E Tests

## Install TestCafe

Ensure that Node.js and yarn are installed on your computer and run the following command:

```sh
cd client/e2e
yarn install
```

## Run Operate backend

To run E2E dedicated backend (Zeebe on port 26503 + Operate on port 8081):

```sh
make start-e2e
```

To rerun (and clean up data), press Ctrl+C and run the same command again.

## Start Development Server

To start dev server and connect it to E2E related backend:

```sh
cd client
yarn start:e2e
```

## Run Tests

```sh
cd e2e
yarn test
```

## Nightly Tests in Browserstack 

Our E2E tests are run every night at 4am automatically. You can trigger a test run manually in Jenkins: https://ci.operate.camunda.cloud/blue/organizations/jenkins/test_e2e_browserstack.

Console logs, network logs and the recorded screen of a test run is accessible in Browserstack: https://automate.browserstack.com/dashboard/v2/?projectIds=975026

## Test local Operate remotely using Browserstack

Ensure that you have the BrowserStackLocal binary installed on your system. This is needed to establish a connection to Browserstack: https://www.browserstack.com/local-testing/automate#establishing-a-local-testing-connection

Ensure that you have set the following environment variables for the Browserstack credentials. They can be found in the dashboard: https://automate.browserstack.com/dashboard/v2/  
`BROWSERSTACK_USERNAME`  
`BROWSERSTACK_ACCESS_KEY`  


### Start BrowserStackLocal

```sh
./BrowserStackLocal --key $BROWSERSTACK_ACCESS_KEY --local-identifier TestCafe --daemon start --parallel-runs 1
```

### Run tests

```sh
yarn run test:browserstack
```

### Stop BrowserStackLocal

```sh
~/dev/BrowserStackLocal --key $BROWSERSTACK_ACCESS_KEY --local-identifier TestCafe --daemon stop 
```