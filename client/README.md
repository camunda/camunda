# Camunda Optimize Frontend

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

## E2E Testing

### Production mode (only for unix systems):

1. Go to root directory of repository
2. Run ``sh ./start-e2e.sh``

### Development mode:

1. Start development server.
2. Run ``yarn run test-e2e``.

Running tests in watch mode:

```bash
yarn run test-e2e-watch
```

## Contributing

Please refer to the [.eslintrc file](https://github.com/camunda/camunda-optimize/blob/master/client/.eslintrc.json) for coding style guidelines.

### Naming Conventions

Please use a capital letter for component files and lower case letters for reducer, service and other files. E.g. use `client/app/App.js` for the App component and `client/app/reducer.js` for the app reducer.

## Documentation

You can find the documentation of the frontend structure in the [Github Wiki](https://github.com/camunda/camunda-optimize/wiki/Frontend-Wiki)
