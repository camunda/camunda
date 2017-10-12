# Camunda Optimize Frontend

## Requirements

Node 6+

## Installation

Install yarn
```bash
npm install -g yarn
```

Install dependencies
```bash
yarn
```

## Development server

```bash
yarn run start-backend
```

Then in new terminal

```bash
yarn run serve
```

Backend also can be started without maven compilation step. It's possible only if there is already
compiled version of optimize backend at ``camunda-optimize/distro/target``.
To do that set ``FAST_BUILD`` environment on scope. For example:

```bash
export FAST_BUILD=1
yarn run start-backend
```

## Production

```bash
yarn run compile
```

Should create ``dist`` folder with built application.

## Unit Testing

To run tests single time:
```bash
yarn run test
```

Watch:
```bash
yarn run test-watch
```

## Contributing

Please refer to the [.eslintrc file](https://github.com/camunda/camunda-optimize/blob/master/client/.eslintrc.json) for coding style guidelines.

### Naming Conventions

Please use a capital letter for component files and lower case letters for reducer, service and other files. E.g. use `client/app/App.js` for the App component and `client/app/reducer.js` for the app reducer.

## Documentation

You can find the documentation of the frontend structure in the [Github Wiki](https://github.com/camunda/camunda-optimize/wiki/Frontend-Wiki)
