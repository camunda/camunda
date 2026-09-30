/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// @ts-check

import fs from 'node:fs/promises';
import path from 'node:path';

import {WORKSPACE_ROOT} from './paths.js';

/**
 * Configuration for downloading specs - single file mode.
 * @typedef {Object} SingleFileDownloadConfig
 * @property {string} branch - Git branch name (e.g., 'stable/8.9')
 * @property {string} file - Path to the single spec file
 * @property {undefined} [directory] - Not used in single-file mode
 */

/**
 * Configuration for downloading specs - directory mode.
 * @typedef {Object} DirectoryDownloadConfig
 * @property {string} branch - Git branch name (e.g., 'main')
 * @property {undefined} [file] - Not used in directory mode
 * @property {string} directory - Path to directory containing spec files
 */

/**
 * Configuration for downloading specs.
 * @typedef {SingleFileDownloadConfig | DirectoryDownloadConfig} DownloadConfig
 */

/**
 * Configuration for generating schemas.
 * @typedef {Object} GenerateConfig
 * @property {string} input - Path to OpenAPI spec entry point (relative to package root)
 * @property {string} output - Output directory for generated files (relative to package root)
 */

/**
 * Configuration for a supported API version.
 * @typedef {Object} VersionConfig
 * @property {DownloadConfig} download - Configuration for downloading specs
 * @property {GenerateConfig} generate - Configuration for generating schemas
 */

/**
 * @type {Record<string, VersionConfig>}
 */
const CONFIG = {
	8.9: {
		download: {
			branch: 'stable/8.9',
			directory: 'zeebe/gateway-protocol/src/main/proto/v2',
		},
		generate: {
			input: 'specs/8.9/rest-api.yaml',
			output: 'lib/8.9/gen',
		},
	},
	'8.10': {
		download: {
			branch: 'stable/8.10',
			directory: 'zeebe/gateway-protocol/src/main/proto/v2',
		},
		generate: {
			input: 'specs/8.10/rest-api.yaml',
			output: 'lib/8.10/gen',
		},
	},
	8.11: {
		download: {
			branch: 'main',
			directory: 'zeebe/gateway-protocol/src/main/proto/v2',
		},
		generate: {
			input: 'specs/8.11/rest-api.yaml',
			output: 'lib/8.11/gen',
		},
	},
};

/** Version name that stands for the release line of the current branch (see `getCurrentVersion`). */
const CURRENT_VERSION = 'current';

function getAvailableVersions() {
	return Object.keys(CONFIG);
}

/**
 * Reads the release line of the current branch (for example `8.11`) from the version in the workspace
 * `package.json` (for example `8.11.0-SNAPSHOT`).
 * @returns {Promise<string>} The release line, which is always a supported version
 */
async function getCurrentVersion() {
	const packageJsonPath = path.join(WORKSPACE_ROOT, 'package.json');
	const {version} = JSON.parse(await fs.readFile(packageJsonPath, 'utf-8'));
	const match = typeof version === 'string' ? version.match(/^(\d+)\.(\d+)\./) : null;

	if (match === null) {
		throw new Error(`Cannot read the release line from version "${version}" in ${packageJsonPath}`);
	}

	const currentVersion = `${match[1]}.${match[2]}`;

	if (!CONFIG[currentVersion]) {
		throw new Error(
			`The current version ${currentVersion} is not supported. Available versions: ${getAvailableVersions().join(', ')}`,
		);
	}

	return currentVersion;
}

/**
 * Returns the requested versions, or every available version when none is requested.
 * `current` is replaced with the release line of the current branch. Duplicates are removed.
 * @param {string[] | undefined} requestedVersions
 * @returns {Promise<string[]>}
 */
async function resolveVersions(requestedVersions) {
	if (requestedVersions === undefined) {
		return getAvailableVersions();
	}

	const versions = new Set();
	for (const requestedVersion of requestedVersions) {
		const version = requestedVersion === CURRENT_VERSION ? await getCurrentVersion() : requestedVersion;

		if (!CONFIG[version]) {
			throw new Error(
				`Unknown version: ${version}. Available versions: ${[...getAvailableVersions(), CURRENT_VERSION].join(', ')}`,
			);
		}

		versions.add(version);
	}

	return [...versions];
}

export {CURRENT_VERSION, getAvailableVersions, getCurrentVersion, resolveVersions, CONFIG};
