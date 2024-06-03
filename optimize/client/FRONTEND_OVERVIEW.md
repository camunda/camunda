# Front-End Application Overview

## Table of Contents

1. [Overview](#overview)
2. [Application Start-Up](#application-start-up)
3. [Routing](#routing)
4. [Proxying API Requests](#proxying-api-requests)
5. [Project Folder Structure](#project-folder-structure)
6. [Development Scripts](#development-scripts)
7. [Text Translations](#text-translations)
8. [Testing](#testing)
   - [Unit tests](#unit-tests)
   - [End-to-End (E2E) tests](#end-to-end-e2e-tests)
9. [Current Migrations](#current-migrations)
   - [Typescript](#typescript)
   - [Carbon UI](#carbon-ui)
   - [Class to functional React Components](#class-to-functional-react-components)
   - [Higher-order component to hooks](#higher-order-component-to-hooks)


## Overview

This document provides an overview of the front-end application. It explains how the app starts, manages routing, handles API request proxying in development, and translates text to the browser's language. Additionally, it describes the current ongoing migration initiatives.

## Application Start-Up

The front-end application is initialized using the `create-react-app` utility which follow the following process:

1. **Application Startup**: The application is started using the command `yarn start`. This spins up a Node.js Development Server which serve the `public/index.html` file.
4. **Initializing React App**: The html file will load the javascript needed for the react application and also the `src/index.js` file, which initializes the React application.
5. **First React Component**: The `src/App.js` file is then loaded as the first React component.
6. **Utilities and Router Initialization**: The `App.js` component initializes various utilities (such as notifications, analytics, translations, and onboarding) and sets up routing for different pages (Home, Report, Dashboard, Analysis, EventsBasedProcess, etc.).

Below is a flow diagram of the start-up process:


<img src="./docs-images/start-up.png">

## Routing

Routing in the application is managed by App.js. We use a hash-based router, which parses everything after the hash sign in the URL to determine the page component to render. Here’s an example of how it works:

- When the application is accessed at the URL (`/#/collections`), the `Home.js` page is loaded.
- When the application is accessed at `/#/report/new`, the `Report.js` component is loaded.

Below is a simplified visual representation of the routing setup:


<img src="./docs-images/routing.png">


## Proxying API Requests

To handle API requests, we use a proxy setup to forward requests from the front-end server (running on `localhost:3000`) to the backend server (running on `localhost:8090`). This is particularly useful during development since we have a seperate servers for front-end and backend. Here is an example of how this works:

1. When the `Home.js` component loads entities by calling `loadEntities()`, it makes a request to `/api/entities`.
2. The request made to `/api/entities` by the front-end is forwarded to `localhost:8090/api/entities` on the backend server which returns the list of entities to the front-end.


## Project Folder Structure

Here is an overview of the `src` folder structure and the purpose of each major directory and file.

### Root Directory
The root `src` directory contains the main entry points and configuration files for the project. Here are the key files:

- **App.js**: The main application component.
- **index.js**: Entry point of the application.
- **setupProxy.js**: Proxy configuration for development server.
- **setupTests.ts**: Setup file for tests.
- **style.scss**: Global styles for the application.

### components

The `components` directory is where the main routes and page components of the application exist. Additionally, it contains the **PrivateRoute** component which contains the logic for protected routes handling.

### modules

The `modules` directory contains everything that is reused across routes and components, from services to global utilities. This directory includes:

- **components**: Reusable components.
- **HOC**: Reusable Higher-Order Components.
- **hooks**: Custom Reusable  React hooks.
- **services**: global services for API calls, formatting and other functionalities.
- **shared-styles**: Shared styles and SCSS files.
- **tracking**: Components and services for mixpanel tracking.
- **translation**: Components and utilities for handling translations. This is explained in details below.
- **request.ts**: Utility for handling requests.
- **types.ts**: global TypeScript type definitions.
- **config.tsx**: Global UI Configuration.

Note: everything in this directory can be imported directly by referencing the directory name (e.g. `import { x } from 'components'`) because of the `wireModules` script that we will explain later in this document.

## Development Scripts
The development scripts exists inside the `scripts` folder. Here is an brief explanation of the scripts we developed:

- **dependencies**: Generates a list of dependencies used in the application. It is usually used during the release process to release a list of dependencies of the front-end and add them to the docs
- **e2e**: This script sets up and executes end-to-end (E2E) tests on the CI using TestCafe. It runs the tests on three browsers in parallel to execute the tests on BrowserStack.
- **generate-data**: used to start to data generator to generate new data. We do not use it anymore since we usually load a postgres dump.
- **ingest-event-data**: It is used to ingest demo data for the event based processes feature
- **start-backend**: This script starts everything needed for development apart from the front end application. It starts the docker-compose.yml to start the engine and elasticsearch, load the demo data into postgres and starts the backend server. it can be triggered using the `yarn start-backend` command.
- **writeModules**: this script runs on package.json "postinstall" hook. It used to create symbolic link of the `modules` folder into the `node_modules` folder. this makes it easier to import stuff from the modules folder (reusable component & services module) without the need to specify the full path.

  For example, if we need to use the request module that exists inside `src/modules/request.ts` we can do the following: `import {...} from 'request'` anywhere in the code base.


## Text Translations

Our application supports multiple languages through a text translation module we developed. All translations are maintained in JSON files, such as `en.json` for English and `de.json` for German.

#### Structure of JSON Files

The JSON files group common features and functionality within nested properties. Each property name follows camelCase conventions for consistency and readability. Here is an example:

```json
// en.json
{
  "login": {
    "username": "Username",
    "password": "Password"
  },
  "report": {
    "instanceCount": "there are {count} instances"
  }
}
```

#### Loading and resolving translations

We have built a module called `translation.tsx` that is responsible for loading and resolving translations based on the user's browser language. This module handles:

1. **Fetching translations**: When the application loads, it issues a request to the backend to fetch the appropriate JSON translation file.
2. **Loading translations**: The translations are then loaded into the front-end as a global object.
3. **Resolving translations**: The module resolves the translations dynamically based on the detected browser language, ensuring that users see the application in their preferred language. It has a `t` function that resolves the translation based on the provided path. e.g. `t('login.username')` returns `Username`. It is also possible to pass values to the translation. e.g. `t('report.instanceCount', {count: 5})` returns `there are 5 instances`.

## Testing

Our front-end application employs a testing strategy that includes both unit tests and end-to-end (E2E) tests.


### Unit tests:
Unit tests are conducted using Jest and Enzyme to ensure that individual components function as expected. The configuration file for unit tests (`setupTests.ts`) sets up the testing environment, including polyfills, adapters, and global functions to handle promises and garbage collection.

For mocking internal or external modules, we usually use `Jest.mock` function in the test file where the module is being used. In some cases, we require the module to be mocked globally. To do that, we place a mock file or folder of the module inside the `src/__mocks__` folder.

### End-to-End (E2E) tests:
End-to-end tests are executed using TestCafe to validate the functionality of the application in a real browser environment. These tests cover user interactions and the integration of various components. Tests are run in three parallel browsers to speed up the process. Different users are configured for each browser to avoid race conditions.

**CSS selectors Management**: CSS selectors are organized in separate files named `<ModuleName>.elements.js`. Common selectors used across multiple tests are stored in `common.elements.js` to avoid duplication.

**Screenshot Generation**: E2E tests can generate screenshots for documentation purposes by running `yarn screenshots`. The `e2e/browserMagic.js` module is sometimes used to add labels and arrows to screenshots, enhancing their utility for documentation.

**CI jobs**: We have two workflows on the CI the run the e2e tests:
1. `optimize-e2e-tests.yml`: It runs the e2e tests on PR/push in a headless version of chrome.
2. `optimize-e2e-tests-browserstack`: runs the e2e tests on a daily schedule in chrome, firefox, internet explorer using browserstack.


## Current Migrations
### Typescript:
In [OPT-6735](https://jira.camunda.com/browse/OPT-6735), we initiated the migration to TypeScript for the Optimize project.
Up to this point, the core services, utilities, and a significant portion of the reusable components have been successfully migrated to TypeScript.
We migrate the codebase incrementally while we are working on other issues. We usually try to migrate at least one module to typescript per issue.

### Carbon UI:
in [#6841](https://github.com/camunda/camunda-optimize/issues/6841), we initiated the migration of the Optimize UI to Carbon.
Up to this point, Most of the UI is already migrated. There are still leftovers (loading states, tooltips, notifications) that we still working on migrating.

### Class to functional React components
he class-based approach in React is obsolete and should not be used anymore. When we encounter a class-based React component, we try to migrate it to a functional component.

### Higher-order component to hooks
We are currently migrating our codebase from using Higher-Order Components (HOCs) to React Hooks. An HOC is a type of wrapper pattern where an HOC function takes a component and returns a new component with extended functionality. While powerful, HOCs can make the code harder to read and follow, especially when multiple HOCs are combined. React Hooks provide a cleaner way to add state and side effects to functional components and are the recommended approach.

It is advised to avoid using HOCs and use hooks instead (e.g., use the useErrorHandling hook instead of the withErrorHandling HOC).
However, if you are dealing with a big class and you do not want or have the time to migrate to functional component, you can still use the HOC in that component.




