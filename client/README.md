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

### Testing components

Add new file in test folder with path similar to component being test.
Test file name should end with ``.test.js``.

For testing use ``chai``, ``chai-dom`` and ``sinon``.
In ``test/testHelpers`` you can find functions that will be useful during testing. 
Like ``mountTemplate`` which adds your template to dom and 
return object with ``node``, ``update`` and ``eventsBus`` properties.
``node`` is parent node of your component. ``update`` is function
which updates your component you can pass mocked state to it and ``eventsBus`` is well events bus.


## Documentation

You can find the documentation of the frontend structure in the [Github Wiki](https://github.com/camunda/camunda-optimize/wiki/Frontend-Wiki)
