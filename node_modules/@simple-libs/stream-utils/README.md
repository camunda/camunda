# @simple-libs/stream-utils

[![ESM-only package][package]][package-url]
[![NPM version][npm]][npm-url]
[![Node version][node]][node-url]
[![Dependencies status][deps]][deps-url]
[![Install size][size]][size-url]
[![Build status][build]][build-url]
[![Coverage status][coverage]][coverage-url]

[package]: https://img.shields.io/badge/package-ESM--only-ffe536.svg
[package-url]: https://nodejs.org/api/esm.html

[npm]: https://img.shields.io/npm/v/@simple-libs/stream-utils.svg
[npm-url]: https://www.npmjs.com/package/@simple-libs/stream-utils

[node]: https://img.shields.io/node/v/@simple-libs/stream-utils.svg
[node-url]: https://nodejs.org

[deps]: https://img.shields.io/librariesio/release/npm/@simple-libs/stream-utils
[deps-url]: https://libraries.io/npm/@simple-libs%2Fstream-utils

[size]: https://packagephobia.com/badge?p=@simple-libs/stream-utils
[size-url]: https://packagephobia.com/result?p=@simple-libs/stream-utils

[build]: https://img.shields.io/github/actions/workflow/status/TrigenSoftware/simple-libs/tests.yml?branch=main
[build-url]: https://github.com/TrigenSoftware/simple-libs/actions

[coverage]: https://coveralls.io/repos/github/TrigenSoftware/simple-libs/badge.svg?branch=main
[coverage-url]: https://coveralls.io/github/TrigenSoftware/simple-libs?branch=main

A small set of utilities for streams.

## Install

```bash
# pnpm
pnpm add @simple-libs/stream-utils
# yarn
yarn add @simple-libs/stream-utils
# npm
npm i @simple-libs/stream-utils
```

## Usage

```ts
import {
  toArray,
  concatBufferStream,
  concatStringStream,
  firstFromStream,
  mergeReadables
} from '@simple-libs/stream-utils'

// Convert a readable stream to an array
await toArray(Readable.from(['foo', 'bar', 'baz']))
// Returns ['foo', 'bar', 'baz']

// Concatenate a stream of buffers into a single buffer
await concatBufferStream(Readable.from([Buffer.from('foo'), Buffer.from('bar')]))
// Returns <Buffer 66 6f 6f 62 61 72>

// Concatenate a stream of strings into a single string
await concatStringStream(Readable.from(['foo', 'bar']))
// Returns 'foobar'

// Get the first value from a stream
await firstFromStream(Readable.from(['foo', 'bar']))
// Returns 'foo'

// Merges multiple Readable streams into a single Readable stream.
// Each chunk will be an object containing the source stream name and the chunk data.
await mergeReadables({
  foo: Readable.from(['foo1', 'foo2']),
  bar: Readable.from(['bar1', 'bar2'])
})
// Returns [{ source: 'foo', chunk: 'foo1' }, { source: 'foo', chunk: 'foo2' }, { source: 'bar', chunk: 'bar1' }, { source: 'bar', chunk: 'bar2' }]
```
