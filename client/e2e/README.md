# E2E Tests

## Install TestCafe

Ensure that Node.js and yarn are installed on your computer and run the following command:

```sh
cd e2e
yarn install
```

## Run Tests

Start the Operate local build:

```sh
make start-backend
cd client && yarn start
```

start the E2E tests

```sh
cd e2e
yarn test
```
