/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// @ts-check

import {parseArgs} from 'node:util';

/** @typedef {import('node:util').ParseArgsOptionsConfig} ParseArgsOptionsConfig */

/** @satisfies {ParseArgsOptionsConfig} */
const COMMON_OPTIONS = {
	version: {type: 'string', short: 'v', multiple: true},
	help: {type: 'boolean', short: 'h', default: false},
};

/**
 * Parses the command line. Every script accepts `-v, --version <version>` (repeatable) and `-h, --help`.
 * @template {ParseArgsOptionsConfig} [ExtraOptions={}]
 * @param {ExtraOptions} [extraOptions] - Script specific options
 */
function parseCliArgs(extraOptions) {
	return parseArgs({options: {...COMMON_OPTIONS, .../** @type {ExtraOptions} */ (extraOptions)}}).values;
}

/**
 * Runs the entry point of a script and exits with code 1 when it fails.
 * @param {() => Promise<void>} main
 */
function runCli(main) {
	main().catch((error) => {
		console.error('\nError:', error.message);
		process.exit(1);
	});
}

export {parseCliArgs, runCli};
