## Tasklist

Tasklist is an graphical and API application to manage user tasks in Zeebe.

## Build and run

To build and run the project with all the dependencies, run the below command:

```sh
make env-up
```

The above command will generate docker containers for all the dependencies and make the web application available in http://localhost:8080/.

In case you want to clean everything to build again, run the below command:

```sh
make env-down
```

In order to run the application locally for debug purposes, you can start only the Tasklist dependencies using:

```sh
docker-compose up elasticsearch zeebe
```

And then start the spring-boot application under webapp folder in your preferred IDE.

### Running E2E tests

We use Testcafe for E2E tests, which are executed on every push to any branch via Github Actions.

To run these tests locally, follow the steps:

1. In the root folder, execute `make env-up` and confirm Tasklist is running by checking `localhost:8080`.
2. In the same root folder, execute `make start-e2e`.
3. In the `client/` folder, execute yarn start:e2e.
4. Lastly, run `yarn test:e2e`.

### Running visual regression tests

On Tasklist we use Playwright for visual regression testing. These tests run on every push on every branch through Github Actions.

To run these locally you can follow the steps below:

1. Inside the client folder run `yarn build:visual-regression`
2. After the build is finished start the Docker container with `yarn start-visual-regression-docker`
3. Inside the container, run `yarn start:visual-regression &`
4. After that, run `yarn playwright visual`

#### Updating screenshots

If you made feature changes and want to purposely wants to update the UI baseline you can follow the steps before, but on step 4 you should run `yarn playwright visual --update-snapshots`. Beware the this will update all screenshots, so make sure you only have the changes you want to update in your branch.

#### Inspecting failures in the CI

Sometimes the visual regression tests might fail in the CI and you want to check why. To achieve that you can download the Playwright report assets (like in the image below), unzip the folder and then run `npx @playwright/test show-report folder-with-unzipped-assets/`.

<img src="/docs_assets/playwright_report.png" alt="Playwright report artifact download" width="500"/>