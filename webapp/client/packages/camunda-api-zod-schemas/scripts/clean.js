/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// @ts-check

import {existsSync} from 'node:fs';
import {rm} from 'node:fs/promises';
import path from 'node:path';

import {runCli} from './cli.js';
import {PACKAGE_ROOT, SPECS_DIR} from './paths.js';
import {CONFIG} from './supported-versions.js';

async function main() {
	console.log('Cleaning generated files...\n');

	const directories = [
		SPECS_DIR,
		...Object.values(CONFIG).map(({generate}) => path.join(PACKAGE_ROOT, generate.output)),
	];
	const existingDirectories = directories.filter((directory) => existsSync(directory));

	for (const directory of existingDirectories) {
		await rm(directory, {recursive: true, force: true});
		console.log(`  Deleted ${path.relative(PACKAGE_ROOT, directory)}/`);
	}

	const deletedCount = existingDirectories.length;
	if (deletedCount === 0) {
		console.log('  Nothing to clean.');
	} else {
		console.log(`\nCleaned ${deletedCount} ${deletedCount === 1 ? 'directory' : 'directories'}.`);
	}
}

runCli(main);
