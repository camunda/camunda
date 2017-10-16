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
yarn start
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
yarn build
```

Should create ``build`` folder with built application.

## Unit Testing

```bash
yarn test
```
