# @simple-libs/child-process-utils

[![ESM-only package][package]][package-url]
[![NPM version][npm]][npm-url]
[![Node version][node]][node-url]
[![Dependencies status][deps]][deps-url]
[![Install size][size]][size-url]
[![Build status][build]][build-url]
[![Coverage status][coverage]][coverage-url]

[package]: https://img.shields.io/badge/package-ESM--only-ffe536.svg
[package-url]: https://nodejs.org/api/esm.html

[npm]: https://img.shields.io/npm/v/@simple-libs/child-process-utils.svg
[npm-url]: https://www.npmjs.com/package/@simple-libs/child-process-utils

[node]: https://img.shields.io/node/v/@simple-libs/child-process-utils.svg
[node-url]: https://nodejs.org

[deps]: https://img.shields.io/librariesio/release/npm/@simple-libs/child-process-utils
[deps-url]: https://libraries.io/npm/@simple-libs%2Fchild-process-utils

[size]: https://packagephobia.com/badge?p=@simple-libs/child-process-utils
[size-url]: https://packagephobia.com/result?p=@simple-libs/child-process-utils

[build]: https://img.shields.io/github/actions/workflow/status/TrigenSoftware/simple-libs/tests.yml?branch=main
[build-url]: https://github.com/TrigenSoftware/simple-libs/actions

[coverage]: https://coveralls.io/repos/github/TrigenSoftware/simple-libs/badge.svg?branch=main
[coverage-url]: https://coveralls.io/github/TrigenSoftware/simple-libs?branch=main

A small set of utilities for child process.

## Install

```bash
# pnpm
pnpm add @simple-libs/child-process-utils
# yarn
yarn add @simple-libs/child-process-utils
# npm
npm i @simple-libs/child-process-utils
```

## Usage

```ts
import {
  exitCode,
  catchProcessError,
  throwProcessError,
  outputStream,
  output
} from '@simple-libs/child-process-utils'

// Wait for a child process to exit and return its exit code
await exitCode(spawn())
// Returns 0 if the process exited successfully, or the exit code if it failed

// Catch error from a child process
await catchProcessError(spawn())
// Returns the error if the process failed, or null if it succeeded

// Throws an error if the child process exits with a non-zero code.
await throwProcessError(spawn())

// Yields the stdout of a child process.
// It will throw an error if the process exits with a non-zero code.
for await (chunk of outputStream(spawn())) {
  console.log(chunk.toString())
}

// Collects the stdout of a child process into a single Buffer.
// It will throw an error if the process exits with a non-zero code.
await output(spawn())
// Returns a Buffer with the stdout of the process
```
