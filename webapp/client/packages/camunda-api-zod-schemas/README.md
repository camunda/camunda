# @camunda/camunda-api-zod-schemas

This is a community-driven open-source project that provides [Zod](https://zod.dev/) schemas and TypeScript type definitions for the Camunda 8 REST API.

It aims to help developers build robust and type-safe applications when interacting with Camunda 8.

The official Camunda 8 REST API documentation can be found here: [https://docs.camunda.io/docs/next/apis-tools/camunda-api-rest/camunda-api-rest-overview/](https://docs.camunda.io/docs/next/apis-tools/camunda-api-rest/camunda-api-rest-overview/)

## Installation

You can install the package using your favorite package manager:

```bash
# pnpm
pnpm add @camunda/camunda-api-zod-schemas

# npm
npm install @camunda/camunda-api-zod-schemas

# yarn
yarn add @camunda/camunda-api-zod-schemas
```

## Usage

The library exports modules that correspond to the different parts of the Camunda API. You can import the schemas and types you need from the main package or specific version sub-modules like `@camunda/camunda-api-zod-schemas/8.8`.

Refer to the exported members from `lib/8.8/index.ts` and other files in the `lib` directory for available schemas and types.

## Publishing a new version

1. Increment the version in `package.json` and push changes to `main`.
2. Run the [Publish Zod Schemas to npm](https://github.com/camunda/camunda/actions/workflows/publish-zod-schemas.yml) GitHub action.
   - By default, the dry-run option is enabled. Uncheck it to publish the new version.
3. Update the `@camunda/camunda-api-zod-schemas` package versions in Operate, Admin and Tasklist.
