# Camunda Optimize Frontend

For an overview of the frontend application, please refer to the [FRONTEND_OVERVIEW.md](./FRONTEND_OVERVIEW.md) document.

## Requirements

Node 20.9.0+
Yarn 1.22.19+
Docker 17.12.0+
Docker Compose 1.29.2+
[Google Cloud SDK](https://cloud.google.com/sdk/docs/install)

## Installation

Install yarn

```bash
npm install -g yarn
```

Install dependencies

```bash
yarn
```

## Login to Docker Registry

Harbor is used to store artifacts that will be downloaded automatically. Use your Okta credentials to log in to Harbor by running the following command:

```bash
docker login registry.camunda.cloud
```

Use `firstname.lastname` as the username.

## Setup Auth0 credentials

Create a file called `.env` in the client root directory (`optimize/client`) with the following content:

```bash
AUTH0_CLIENTSECRET=
AUTH0_USEREMAIL=
AUTH0_USERPASSWORD=
```

Retrieve the credentials from the [vault](https://vault.int.camunda.com/ui/vault/secrets/secret/show/products/optimize/ci/camunda-optimize) and paste them into the `.env` file you just created.

## Development server

To run Optimize in self-managed mode:

```bash
yarn start-backend
```

To run Optimize in cloud mode:

```bash
yarn start-backend-cloud
```

> **Tip:**
> Ensure no active Docker containers are running on the Optimize ports (e.g., 8090) before starting the backend.

Then, in a new terminal:

```bash
yarn start
```

### Running the frontend with OpenSearch

In addition to the above commands, which run Optimize with Elasticsearch, you can run the development server with OpenSearch by executing the following commands:

To run in self-managed mode:

```bash
yarn start-backend:opensearch
```

To run in cloud mode:

```bash
yarn start-backend-cloud:opensearch
```

After that, you can run the frontend with `yarn start` as mentioned above.

### Troubleshooting

If you encounter errors, try running the following command from the root (`optimize`) folder:

```bash
mvn clean install -Pit
```

## Production

To build the application:

```bash
yarn build
```

This will create a `dist` folder with the built application.

## Unit testing

To run unit tests:

```bash
yarn test
```

### Mac issues

If you’re using a Mac and `yarn test` fails with errors like:

```
2017-10-24 13:57 node[16138] (FSEvents.framework) FSEventStreamStart: register_with_server: ERROR: f2d_register_rpc() => (null) (-22)
2017-10-24 13:57 node[16138] (FSEvents.framework) FSEventStreamStart: register_with_server: ERROR: f2d_register_rpc() => (null) (-22)
2017-10-24 13:57 node[16138] (FSEvents.framework) FSEventStreamStart: register_with_server: ERROR: f2d_register_rpc() => (null) (-22)
events.js:160
      throw er; // Unhandled 'error' event
      ^

Error: Error watching file for changes: EMFILE
    at exports._errnoException (util.js:1020:11)
    at FSEvent.FSWatcher._handle.onchange (fs.js:1420:11)
error Command failed with exit code 1.

```

You can resolve this by installing [watchman](https://facebook.github.io/watchman/docs/install.html).

### Linux issues

If you’re using a Linux system and `yarn test` fails with errors like:

```
fs.js:1378
    throw error;
    ^


Waiting...Fatal error: watch ENOSPC
    at _errnoException (util.js:1024:11)
    at FSWatcher.start (fs.js:1376:19)
    ....
```

You can find a solution [here](https://stackoverflow.com/a/17437601).

## E2E testing

Ensure the backend is started with `yarn start-backend` and the frontend with `yarn start` before running the tests.

```bash
yarn run e2e
```
