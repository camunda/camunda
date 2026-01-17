/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// @ts-check

import {rm} from 'node:fs/promises';
import {existsSync} from 'node:fs';
import {join} from 'node:path';
import {CONFIG} from './supported-versions.js';

const SPECS_DIR = 'specs';

/**
 * Deletes a directory if it exists.
 * @param {string} dir - Directory path to delete
 * @returns {Promise<boolean>} - True if deleted, false if didn't exist
 */
async function deleteIfExists(dir) {
	if (existsSync(dir)) {
		await rm(dir, {recursive: true, force: true});
		return true;
	}
	return false;
}

async function main() {
	console.log('Cleaning generated files...\n');

	let deletedCount = 0;

	const specsPath = join(process.cwd(), SPECS_DIR);
	if (await deleteIfExists(specsPath)) {
		console.log(`  Deleted ${SPECS_DIR}/`);
		deletedCount++;
	}

	for (const version of Object.keys(CONFIG)) {
		const genPath = join(process.cwd(), CONFIG[version].generate.output);
		if (await deleteIfExists(genPath)) {
			console.log(`  Deleted ${CONFIG[version].generate.output}/`);
			deletedCount++;
		}
	}

	if (deletedCount === 0) {
		console.log('  Nothing to clean.');
	} else {
		console.log(`\nCleaned ${deletedCount} ${deletedCount === 1 ? 'directory' : 'directories'}.`);
	}
}

main().catch((error) => {
	console.error('Clean failed:', error);
	process.exit(1);
});
