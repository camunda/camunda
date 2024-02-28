# Operate

## Documentation

- [Backend Documentation](./webapp)
- [Frontend Documentation](./client)
- [Contribution Guidelines](https://github.com/camunda/camunda-operate/wiki/Contributing-to-Operate)
- [Issue Tracker](https://app.camunda.com/jira/secure/RapidBoard.jspa?rapidView=61)
- [Zeebe](https://zeebe.io)

### Modules

![modulesimg](https://user-images.githubusercontent.com/3302415/148035876-9d29b64b-f2ed-4402-b6b8-756401a1467c.png)

_Source: [modules.puml](https://github.com/camunda/operate/blob/master/modules.puml)_

## Building locally

To build Operate locally, following Maven configuration is needed: https://confluence.camunda.com/display/HAN/Artifactory#Artifactory-ViaMaven

## Running locally

### Use maven

To run the application locally without docker you can use

```
make start-backend
```

This starts elasticsearch and zeebe docker containers, then installs operate locally and starts the server.
You can shutdown the application with Control-C.
At the first run it takes some time to resolve all dependencies.

### Use docker

To run the application locally you can use `docker`, `docker-compose` and
`make`. Make sure to have a recent version of these tools installed
locally: you should be able to run these commands on your shell without
`sudo`.

Windows users need to install `make` manually on their shell. You can find
instructions on how to do it
[here](https://gist.github.com/evanwill/0207876c3243bbb6863e65ec5dc3f058#make).

To spawn the full local environment, run this command in the root folder:

```
make env-up
```

The app will be running at `localhost:8080`.

To stop:

```
make env-down
```

To see the status of the environment, you can run:

```
make env-status
```

This command should pull/build the necessary containers to run the
application locally, the first run might take a while. This includes
a local elasticsearch, zeebe, operate backend and frontend.

You can clean your local docker environment using:

```
make env-clean
```

This will delete all dangling images and containers. It might be useful
when you run out of space or if you encounter any problem with the docker
daemon.

## Known issues

### "max virtual memory" error on Mac/Linux

Sometimes Elasticsearch will not run reporting the error in log:

```
max virtual memory areas vm.max_map_count [65530] likely too low, increase to at least [262144]
```

In this case following command might fix the issue:

```
sudo sysctl -w vm.max_map_count=262144
```

To set this value permanently, update the `vm.max_map_count` setting in /etc/sysctl.conf. For more info see here: https://www.elastic.co/guide/en/elasticsearch/reference/current/vm-max-map-count.html

## Commit Message Guidelines

- **feat** (new feature for the user, not a new feature for build script)
- **fix** (bug fix for the user, not a fix to a build script)
- **docs** (changes to the documentation)
- **style** (changes to css, styling, etc; no business logic change)
- **refactor** (refactoring production code, eg. renaming a variable)
- **test** (adding missing tests, refactoring tests; no production code change)
- **chore** (updating grunt tasks etc; no production code change)

## Testing environments

- The **staging** environment is available here: https://stage.operate.camunda.cloud/ . Every commit to master (successfully built) will be published to stage automatically.
- Moreover, to deploy a PR at $branch_name.operate.camunda.cloud (e.s. `amazing-feature.operate.camunda.cloud`) please add the `deploy-preview` label to the PR.

## Branch name flags

- Every branch that starts with `fe-` (e.g. `fe-ope-123-frontend-fix`) will skip backend tests in CI.

License: This repository contains files subject to a commercial license.

## Running visual regression tests

On Operate we use Playwright for visual regression testing. These tests run on every push on every branch through Github Actions.

To run these locally you can follow the steps below:

1. Inside the client folder run `yarn build:visual-regression`
2. After the build is finished start the Docker container with `yarn start-visual-regression-docker`
3. Inside the container, run `yarn start:visual-regression &`
4. After that, run `yarn playwright visual`

After the tests run, test report is saved locally in operate/client/playwright-report. In case step 4 fails with `Failed to open browser on ...` , run the following command inside client folder to see the test results: `npx @playwright/test show-report playwright-report/`

#### Updating screenshots

If you made feature changes and want to purposely wants to update the UI baseline you can follow the steps before, but on step 4 you should run `yarn playwright visual --update-snapshots`. Beware the this will update all screenshots, so make sure you only have the changes you want to update in your branch.

#### Inspecting failures in the CI

Sometimes the visual regression tests might fail in the CI and you want to check why. To achieve that you can download the Playwright report assets (like in the image below), unzip the folder and then run `npx @playwright/test show-report folder-with-unzipped-assets/`.

<img src="/docs_assets/playwright_report.png" alt="Playwright report artifact download" width="500"/>

## Running e2e tests

Playwright e2e tests run on every push on every branch through Github Actions.

To run these locally you can follow the steps below:

### Run Operate backend

To run E2E dedicated backend (Zeebe on port 26503 + Operate on port 8081):

```
make start-e2e
```

To rerun (and clean up data), press Ctrl+C and run the same command again.

### Start Development Server

To start dev server and connect it to E2E related backend:

```
cd client
yarn start:e2e
```

### Run Tests

```
yarn test:e2e
```

