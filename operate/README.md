# Operate


## Documentation

- [Backend Documentation](./webapp)
- [Frontend Documentation](./client)

## Running locally

### Use Camunda 8 with Elasticsearch docker container

To run the application locally you can use `docker`, `docker-compose` and
`make`. Make sure to have a recent version of these tools installed
locally: you should be able to run these commands on your shell without
`sudo`.

Windows users need to install `make` manually in their shell. You can find
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

Sometimes Elasticsearch will not run, reporting the error in the log:

```
max virtual memory areas vm.max_map_count [65530] likely too low, increase to at least [262144]
```

In this case, the following command might fix the issue:

```
sudo sysctl -w vm.max_map_count=262144
```

To set this value permanently, update the `vm.max_map_count` setting in /etc/sysctl.conf. For more info see here: https://www.elastic.co/guide/en/elasticsearch/reference/current/vm-max-map-count.html

## Preview environment

- You can deploy a preview of the self managed environment from a PR. To do this, add the `deploy-preview` label to the PR.
- Preview environments can also be deployed programmatically via the `preview-env-build-and-deploy.yml` workflow, which accepts:
  - `app-name`: The ArgoCD application name (max 15 chars)
  - `labels`: Additional ArgoCD app labels (comma or newline-separated key=value pairs)
- A weekly smoke test runs automatically to verify preview environment functionality.

## Branch name flags

- Every branch that starts with `fe-` (e.g. `fe-ope-123-frontend-fix`) will skip backend tests in CI.

## Running visual regression tests

On Operate we use Playwright for visual regression testing. These tests run on every push on every branch through Github Actions.

To run these locally you can follow the steps below:

1. Inside the client folder run `npm run build:visual-regression`
2. After the build is finished start the Docker container with `npm run start-visual-regression-docker`
3. Inside the container, run `npm run start:visual-regression &`
4. After that, run `npm run test:visual`

After the tests run, test report is saved locally in operate/client/playwright-report. In case step 4 fails with `Failed to open browser on ...` , run the following command inside client folder to see the test results: `npx @playwright/test show-report playwright-report/`

### Updating visual regression screenshots

If you made feature changes and intentionally want to update the UI baseline, follow the steps above, but in step 4 run `npm run test:visual -- --update-snapshots`. Be aware that this will update all screenshots, so make sure you only have the changes you want to update in your branch.

### Inspecting failures in the CI

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

### Run Tests

```
npm run test:e2e
```

## Starting up Operate for updating the docs screenshots

### Run Operate backend

```
make env-up
```

### Start Development Server

```
cd client
npm run start
```

### Run the docs screenshots generation (in client directory)

```
npm run generate-screenshots
```

Note that this automation is currently not triggered automatically, so to trigger it for the main branch, this has to be done manually via the [action page](https://github.com/camunda/camunda/actions/workflows/operate-update-docs-screenshots.yml).
