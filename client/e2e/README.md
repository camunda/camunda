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
yarn test:e2e
```
