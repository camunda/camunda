This project was bootstrapped with [Create React App](https://github.com/facebook/create-react-app).

## Available Scripts

In the project directory, you can run:

### `npm run start`

Runs the app in the development mode.<br />
Open [http://localhost:3000](http://localhost:3000) to view it in the browser.

The page will reload if you make edits.<br />
You will also see any lint errors in the console.

### `npm run test`

Launches the test runner in the interactive watch mode.<br />

### `npm run build`

Builds the app for production to the `build` folder.<br />
It correctly bundles React in production mode and optimizes the build for the best performance.

The build is minified and the filenames include the hashes.<br />
Your app is ready to be deployed!

## Run Tasklist backend

While developing the frontend, you might need to have Elasticsearch, Zeebe and the backend up and running.
In this case you can run the following command in the root of the project:

```sh
make env-up
```

To run Operate using the same data set use following command:

```sh
make operate-up
```

Operate UI will be available on port 8088 (http://localhost:8088).

You can then destroy the environment by pressing Ctrl+C and running:

```sh
make env-down
```

## Generating snapshots for camunda-docs

We use playwright to automate taking snapshots of our application. This utilizes the visual regression
playwright environment to take snapshots. These are currently 1280x720 pixel images (unless overridden).

To run the snapshot tests:

```
npm run start-visual-regression-docker
# in the docker environment
npm run start:visual-regression &
npm run test:docs --update-snapshots
```
