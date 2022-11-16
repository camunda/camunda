# Camunda Optimize Frontend

## Requirements

Node 14.15.4+
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

Maybe it requires an addition for the readme you mentioned on how to long in to harbor.
docker login registry.camunda.cloud via firstname.lastname

Harbor is used to store artifacts that will be downloaded automatically. You can use your Okta credentials after
running the below command to log in to Harbor.

```bash
docker login registry.camunda.cloud
```

## Setup Auth0 credentials

Create a file called `.env` in the root directory (client) that contains the following:
```bash
AUTH0_CLIENTSECRET=
AUTH0_USEREMAIL=
AUTH0_USERPASSWORD=
```

Retrieve the credentials from the [vault](https://vault.int.camunda.com/ui/vault/secrets/secret/show/products/optimize/ci/jenkins) and paste them into the .env file you have just created


## Development server

```bash
yarn run start-backend
```

The above command will run Optimize in Camunda Platform 7 mode.
if you want to run Optimize in cloud or self managed mode, use one of the following:

```bash
yarn run start-backend-cloud
yarn run start-backend-sm
```
> **Tip:**
  Make sure you don't have any active docker containers running on the optimize ports (e.g. 8090, etc) before starting the backend.

Then in new terminal

```bash
yarn start
```

### Problems that may happen

If for some reason you are getting error try running from the root folder:

```bash
mvn clean install -Pit
```

process stops after elasticsearch is started - delete old data folder of ES

```bash
rm -rf ../distro/traget/distro/server/elasticsearch-6.0.0/data
```

## Production

```bash
yarn build
```

Should create ``build`` folder with built application.

## Unit Testing

```bash
yarn test
```

### Problems with Mac

If you’re running a Mac and `yarn test` fails with something like:

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

you can make the tests run by installing [watchman](https://facebook.github.io/watchman/docs/install.html).

### Problems with Linux

If you're running a Linux system and `yarn test` fails with something like:

```
fs.js:1378
    throw error;
    ^


Waiting...Fatal error: watch ENOSPC
    at _errnoException (util.js:1024:11)
    at FSWatcher.start (fs.js:1376:19)
    ....
```

then you can find [here](https://stackoverflow.com/a/17437601) a solution for this.

## E2E Testing

Make sure you started the backend with `yarn run start-backend` and the frontend with `yarn start`.

```bash
yarn run e2e
```
