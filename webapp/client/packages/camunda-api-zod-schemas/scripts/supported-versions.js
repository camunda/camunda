/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// @ts-check

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
export const CONFIG = {
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
			branch: 'main',
			directory: 'zeebe/gateway-protocol/src/main/proto/v2',
		},
		generate: {
			input: 'specs/8.10/rest-api.yaml',
			output: 'lib/8.10/gen',
		},
	},
};

function getAvailableVersions() {
	return Object.keys(CONFIG);
}

export {getAvailableVersions};
