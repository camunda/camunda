# Camunda Optimize Frontend

## Installation

Install yarn
```bash
npm install -g yarn
```

Go to ``scripts/`` and copy ``be-config.default.js`` as ``be-config.js``.
Edit ``elastic-config.js`` so that ``elastic`` property points to your elastic search executable
and ``version`` is current version number of optimize (needed for finding jar file with backend api).

Example:

```javascript
module.exports = {
  elastic: '/home/user/elasticsearch-5.1.1/bin/elasticsearch', // path to elastic search binary
  version: '1.0.0'
};
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

## Testing

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
