# E2E Tests

## Install TestCafe

Ensure that Node.js and yarn are installed on your computer and run the following command:

```sh
cd client/e2e
yarn install
```

## Run Tasklist backend

Prerequisite: Elasticsearch is running on port 9200 (e.g. with `make env-up` command).

To run E2E dedicated backend (Zeebe on port 26503 + Tasklist on port 8081):

```sh
make start-e2e
```

To rerun (and clean up data), run the same command again.

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
