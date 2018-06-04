# Operate Frontend

> **Notice:** Make sure to have [yarn](https://yarnpkg.com/en/docs/install) installed in your machine

## Installation

```sh
yarn
```

You may need to run this command with administrator privileges, as the [postinstall script](./scripts/wireModules.js) creates a symlinked directory.

## Actions

### Start Development Server

```sh
yarn start
```

### Run tests

```sh
yarn test
```

## Writing Components

Related files should be close to each other. React components usually consist of a directory with the following files:

- Component.js
- Component.test.js
- service.js
- service.test.js
- index.js

### Component.js

Should contain a default export with the Component. The component implementation should take care of everything directly related to rendering, lifecycle and state management. Complex operations or backend communication should not be in this file, but imported from the service.js file.

### Component.test.js

Should test the React component. All imports from the component under test should be mocked. We are using [jest](https://github.com/facebook/jest), [enzyme](https://github.com/airbnb/enzyme) as well as [jest-enzyme](https://github.com/FormidableLabs/enzyme-matchers).

### service.js

Should contain all business logic and manage backend communication for a component. This file should be independent from React.

### service.test.js

Should test the service. You should not need enzyme here as the service should be independent from React or any rendered component.

### index.js

Defines the public interface of the directory. Usually re-exports the default component export as a named export. In some cases, it might also export other resources.

## Developing UI components

### The `src/modules` directory

Some components are used throughout the whole application. To avoid long parent chains when importing those (`import {Input} from '../../../components'`), we are utilizing the module resolution mechanism of npm, so that we can write `import {Input} from 'components'`. To do so, the [`src/modules`](src/modules) directory is symlinked to `src/node_modules`. That way, everything in the [`src/modules`](src/modules) directory is available in the whole application without the need of relative paths.

### Forwarding Refs

Most UI components are React wrappers around some DOM elements. Those components should use [ref forwarding](https://reactjs.org/docs/forwarding-refs.html) to give the application access to the underlying element, e.g. for focus management.

## Automatic Formatting

We use [prettier](https://prettier.io/) to automatically format the Javascript sourcecode. It automatically runs before any commit using [husky](https://github.com/typicode/husky) and [lint-staged](https://github.com/okonet/lint-staged). It is recommended to add a prettier plugin to your IDE:

- [VSCode](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode)

## Further Reading

This project was bootstrapped using [create-react-app](https://github.com/facebook/create-react-app), so if you want to find out how something related to the build process works, check out their [User Guide](https://github.com/facebook/create-react-app/blob/master/packages/react-scripts/template/README.md#table-of-contents).
