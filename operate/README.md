# Operate

## Documentation

- [Backend Documentation](./webapp)
- [Frontend Documentation](./client)

## Running locally

### Use maven

To run the application locally without docker you can use

```
make start-backend
```

This starts elasticsearch and zeebe docker containers, then installs operate locally and starts the server.
You can shutdown the application with Control-C.
At the first run it takes some time to resolve all dependencies.

### Use Camunda 8 with Elasticsearch docker container

To run the application locally you can use `docker`, `docker-compose` and
`make`. Make sure to have a recent version of these tools installed
locally: you should be able to run these commands on your shell without
`sudo`.

Windows users need to install `make` manually on their shell. You can find
instructions on how to do it
[here](https://gist.github.com/evanwill/0207876c3243bbb6863e65ec5dc3f058#make).

#### Zeebe + Operate + Elasticsearch

To run Camunda 8 with Zeebe and Operate profiles, run this command in the operate folder:

```
make env-up
```

The Operate webapp will be running at `localhost:8080/operate`.

#### Zeebe + Operate + Identity + Elasticsearch

To run Camunda 8 with Zeebe, Operate and Identity profiles, run this command in the operate folder:

```
make env-identity-up
```

The Operate webapp will be running at `localhost:8080/operate`.

The Identity webapp will be running at `localhost:8080/identity`.

#### Stop dev environment

```
make env-down
```

#### Check status of the environment

```
make env-status
```

This command should pull/build the necessary containers to run the
application locally, the first run might take a while. This includes
a local elasticsearch, zeebe, operate backend and frontend.

#### Clean your local docker environment

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

## Testing environments

- The **staging** environment is available here: https://stage.operate.camunda.cloud/ . Every commit to master (successfully built) will be published to stage automatically.
- Moreover, to deploy a PR at $branch_name.operate.camunda.cloud (e.s. `amazing-feature.operate.camunda.cloud`) please add the `deploy-preview` label to the PR.

## Branch name flags

- Every branch that starts with `fe-` (e.g. `fe-ope-123-frontend-fix`) will skip backend tests in CI.

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

Run regular backend first (it must be run in parallel in order to have access to Elasticsearch):

```
make env-up
```

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

## Running Operate Update screenshots automation

### Run Operate backend

```
make env-up
```

### Start Development Server

```
cd client
yarn start
```

### Run Operate Update screenshots automation locally (in client directory)

```
yarn generate-screenshots
```

Note that this automation is currently not triggered automatically, so to trigger it for the main branch, this has to be done manually via the [action page](https://github.com/camunda/camunda/actions/workflows/operate-update-docs-screenshots.yml).
