/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// @ts-check

import {adapterOas} from '@kubb/adapter-oas';
import {createKubb} from '@kubb/core';
import {parserTs} from '@kubb/parser-ts';
import {pluginTs} from '@kubb/plugin-ts';
import {pluginZod} from '@kubb/plugin-zod';
import {defineConfig} from 'kubb/config';
import fs from 'node:fs/promises';
import path from 'node:path';

import {parseCliArgs, runCli} from './cli.js';
import {PACKAGE_ROOT, SPECS_DIR} from './paths.js';
import {preprocessSpec} from './preprocess-spec.js';
import {CONFIG, getAvailableVersions, resolveVersions} from './supported-versions.js';

/** @typedef {import('./supported-versions.js').GenerateConfig} GenerateConfig */

/**
 * Output options of a Kubb plugin. The generated files are not type-checked.
 * @param {string} outputPath - Output directory, relative to the version output directory
 */
const pluginOutput = (outputPath) => ({output: {path: outputPath, banner: '// @ts-nocheck'}});

/**
 * Generates Zod schemas and TypeScript types for a specific version.
 * @param {string} version - The API version (e.g., '8.9')
 * @param {GenerateConfig} config - The generation configuration
 * @returns {Promise<void>}
 */
async function generateSchemas(version, config) {
	const inputPath = path.join(PACKAGE_ROOT, config.input);
	const outputPath = path.join(PACKAGE_ROOT, config.output);

	try {
		await fs.access(inputPath);
	} catch {
		throw new Error(`OpenAPI spec not found at ${config.input}. Run 'npm run download-specs' first.`);
	}

	console.log(`Generating schemas for ${version}...`);
	console.log(`  Input: ${config.input}`);
	console.log(`  Output: ${config.output}`);

	const preprocessedDir = path.join(SPECS_DIR, '.preprocessed', version);
	await preprocessSpec(path.dirname(inputPath), preprocessedDir);

	await fs.rm(outputPath, {recursive: true, force: true});

	const {files} = await createKubb(
		defineConfig({
			root: PACKAGE_ROOT,
			input: path.join(preprocessedDir, path.basename(inputPath)),
			adapter: adapterOas({integerType: 'number'}),
			output: {
				path: outputPath,
				barrel: {type: 'named'},
			},
			parsers: [parserTs({extension: {'.ts': '.js'}})],
			plugins: [pluginTs(pluginOutput('./types')), pluginZod(pluginOutput('./zod'))],
		}),
	).build();

	console.log(`  Generated ${files.length} files`);
}

function printHelp() {
	const availableVersions = getAvailableVersions().join(', ');
	console.log(`Usage: node generate-schemas.js [options]
Options:
  -v, --version <version>  Generate only the specified version (can be used multiple times)
  -h, --help               Show this help message
Available versions: ${availableVersions}
Examples:
  node generate-schemas.js                    # Generate all versions
  node generate-schemas.js --version 8.9      # Generate only 8.9
  node generate-schemas.js -v 8.9 -v 8.10      # Generate 8.9 and 8.10
Note: Run 'npm run download-specs' first to download the OpenAPI specs.
`);
}

async function main() {
	const {version: requestedVersions, help} = parseCliArgs();

	if (help) {
		printHelp();
		return;
	}

	const versionsToGenerate = resolveVersions(requestedVersions);

	console.log('Generating Zod schemas and TypeScript types...\n');

	for (const version of versionsToGenerate) {
		await generateSchemas(version, CONFIG[version].generate);
	}

	console.log('\nAll schemas generated successfully.');
}

runCli(main);
