/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// @ts-check

import {build} from '@kubb/core';
import {pluginOas} from '@kubb/plugin-oas';
import {pluginTs} from '@kubb/plugin-ts';
import {pluginZod} from '@kubb/plugin-zod';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

import {CONFIG, getAvailableVersions} from './supported-versions.js';

/** @typedef {import('./supported-versions.js').GenerateConfig} GenerateConfig */

/**
 * Parsed command line arguments.
 * @typedef {Object} ParsedArgs
 * @property {string[] | null} versions - Requested versions to generate, or null for all
 * @property {boolean} help - Whether help flag was passed
 */

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PACKAGE_ROOT = path.resolve(__dirname, '..');

/**
 * Generates Zod schemas and TypeScript types for a specific version.
 * @param {string} version - The API version (e.g., '8.8')
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

	await fs.rm(outputPath, {recursive: true, force: true});

	const {error, files} = await build({
		config: {
			root: PACKAGE_ROOT,
			input: {
				path: inputPath,
			},
			output: {
				path: outputPath,
				extension: {
					'.ts': '.js',
				},
			},
			plugins: [
				pluginOas(),
				pluginTs({
					output: {
						path: './types',
						banner: '// @ts-nocheck',
					},
				}),
				pluginZod({
					output: {
						path: './zod',
						banner: '// @ts-nocheck',
					},
				}),
			],
		},
	});

	if (error) {
		throw error;
	}

	console.log(`  Generated ${files.length} files`);
}

/**
 * Parses command line arguments.
 * @returns {ParsedArgs} Parsed arguments
 */
function parseArgs() {
	const args = process.argv.slice(2);
	/** @type {ParsedArgs} */
	const result = {versions: null, help: false};

	for (let index = 0; index < args.length; index++) {
		const arg = args[index];

		if (arg === '--help' || arg === '-h') {
			result.help = true;
		} else if (arg === '--version' || arg === '-v') {
			const value = args[++index];
			if (!value) {
				throw new Error('--version requires a value');
			}
			if (result.versions === null) {
				result.versions = [];
			}
			result.versions.push(value);
		} else {
			throw new Error(`Unknown argument: ${arg}`);
		}
	}

	return result;
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
  node generate-schemas.js -v 8.8 -v 8.9      # Generate 8.8 and 8.9

Note: Run 'npm run download-specs' first to download the OpenAPI specs.
`);
}

async function main() {
	const {versions: requestedVersions, help} = parseArgs();

	if (help) {
		printHelp();
		return;
	}

	const availableVersions = getAvailableVersions();
	const versionsToGenerate = requestedVersions || availableVersions;

	for (const version of versionsToGenerate) {
		if (!CONFIG[version]) {
			throw new Error(`Unknown version: ${version}. Available versions: ${availableVersions.join(', ')}`);
		}
	}

	console.log('Generating Zod schemas and TypeScript types...\n');

	for (const version of versionsToGenerate) {
		await generateSchemas(version, CONFIG[version].generate);
	}

	console.log('\nAll schemas generated successfully.');
}

main().catch((error) => {
	console.error('\nError:', error.message);
	process.exit(1);
});
