# E2E Tests

## Install TestCafe

Ensure that Node.js and npm are installed on your computer and run the following command:

```sh
npm install -g testcafe
```

## Run Tests

Start the Operate local build:

```sh
make start-backend
cd client && yarn start
```

start the E2E tests

```sh
testcafe chrome ./client/e2e/tests/*.js
```
