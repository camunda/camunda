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
import {fileURLToPath} from 'node:url';

/**
 * Configuration for a single-file spec (e.g., 8.8).
 * @typedef {Object} SingleFileSpecConfig
 * @property {string} branch - Git branch name (e.g., 'stable/8.8')
 * @property {string} file - Path to the single spec file
 * @property {undefined} [directory] - Not used in single-file mode
 */

/**
 * Configuration for a directory-based spec (e.g., 8.9).
 * @typedef {Object} DirectorySpecConfig
 * @property {string} branch - Git branch name (e.g., 'main')
 * @property {undefined} [file] - Not used in directory mode
 * @property {string} directory - Path to directory containing spec files
 */

/**
 * Spec configuration - either single-file or directory-based.
 * @typedef {SingleFileSpecConfig | DirectorySpecConfig} SpecConfig
 */

/**
 * Entry returned from GitHub Contents API for a file.
 * @typedef {Object} GitHubFileEntry
 * @property {string} name - File name
 * @property {string} downloadUrl - URL to download the raw file
 */

/**
 * Raw file entry from GitHub Contents API.
 * @see https://docs.github.com/en/rest/repos/contents#get-repository-content
 * @typedef {Object} GitHubContentsEntry
 * @property {string} name - File name
 * @property {string} path - Full path in repository
 * @property {string} sha - Git blob SHA
 * @property {number} size - File size in bytes
 * @property {string} url - API URL for this content
 * @property {string} html_url - GitHub web URL
 * @property {string} git_url - Git blob URL
 * @property {string | null} download_url - Raw download URL (null for directories)
 * @property {'file' | 'dir' | 'symlink' | 'submodule'} type - Entry type
 */

/**
 * Parsed command line arguments.
 * @typedef {Object} ParsedArgs
 * @property {string[] | null} versions - Requested versions to download, or null for all
 * @property {boolean} help - Whether help flag was passed
 */

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PACKAGE_ROOT = path.resolve(__dirname, '..');
const SPECS_DIR = path.join(PACKAGE_ROOT, 'specs');

const GITHUB_RAW_BASE = 'https://raw.githubusercontent.com/camunda/camunda';
const GITHUB_API_BASE = 'https://api.github.com/repos/camunda/camunda';

/**
 * Configuration for each API version.
 * Add new versions here as they become available.
 *
 * For single-file specs, use `file` property.
 * For directory-based specs (multiple YAML files), use `directory` property.
 *
 * @type {Record<string, SpecConfig>}
 */
const SPECS_CONFIG = {
	8.8: {
		branch: 'stable/8.8',
		file: 'zeebe/gateway-protocol/src/main/proto/rest-api.yaml',
	},
	8.9: {
		branch: 'main',
		directory: 'zeebe/gateway-protocol/src/main/proto/v2',
	},
};

/**
 * Downloads a file from the given URL.
 * @param {string} url - The URL to download from
 * @returns {Promise<string>} - The file contents
 */
async function downloadFile(url) {
	const response = await fetch(url);

	if (!response.ok) {
		throw new Error(`Failed to download ${url}: ${response.status} ${response.statusText}`);
	}

	return response.text();
}

/**
 * Lists all YAML files in a GitHub directory using the Contents API.
 * @param {string} branch - The branch name
 * @param {string} directoryPath - The path to the directory
 * @returns {Promise<GitHubFileEntry[]>} List of YAML files with their download URLs
 */
async function listYamlFiles(branch, directoryPath) {
	const url = `${GITHUB_API_BASE}/contents/${directoryPath}?ref=${branch}`;

	const response = await fetch(url, {
		headers: {
			Accept: 'application/vnd.github.v3+json',
			'User-Agent': 'camunda-api-zod-schemas',
		},
	});

	if (!response.ok) {
		if (response.status === 403) {
			const rateLimitRemaining = response.headers.get('x-ratelimit-remaining');
			if (rateLimitRemaining === '0') {
				const resetTime = response.headers.get('x-ratelimit-reset');
				const resetDate = resetTime ? new Date(parseInt(resetTime, 10) * 1000) : null;
				throw new Error(
					`GitHub API rate limit exceeded. ${resetDate ? `Resets at ${resetDate.toLocaleTimeString()}.` : ''}`,
				);
			}
		}
		throw new Error(`Failed to list directory ${directoryPath}: ${response.status} ${response.statusText}`);
	}

	/** @type {GitHubContentsEntry[]} */
	const files = await response.json();

	return files
		.filter((file) => file.type === 'file' && file.name.endsWith('.yaml') && file.download_url !== null)
		.map((file) => ({
			name: file.name,
			downloadUrl: /** @type {string} */ (file.download_url),
		}));
}

/**
 * Downloads a single file spec (8.8 style).
 * @param {string} version - The API version
 * @param {SingleFileSpecConfig} config - The configuration for this version
 * @returns {Promise<void>}
 */
async function downloadSingleFileSpec(version, config) {
	const url = `${GITHUB_RAW_BASE}/${config.branch}/${config.file}`;
	const outputDir = path.join(SPECS_DIR, version);
	const outputFile = path.join(outputDir, 'rest-api.yaml');

	console.log(`Downloading ${version} spec from ${url}...`);

	const content = await downloadFile(url);

	await fs.mkdir(outputDir, {recursive: true});
	await fs.writeFile(outputFile, content, 'utf-8');

	console.log(`  Saved to ${path.relative(PACKAGE_ROOT, outputFile)}`);
}

/**
 * Downloads a directory of spec files (8.9 style).
 * @param {string} version - The API version
 * @param {DirectorySpecConfig} config - The configuration for this version
 * @returns {Promise<void>}
 */
async function downloadDirectorySpec(version, config) {
	console.log(`Fetching file list for ${version} from ${config.directory}...`);

	const files = await listYamlFiles(config.branch, config.directory);
	console.log(`  Found ${files.length} YAML files`);

	const outputDir = path.join(SPECS_DIR, version);
	await fs.mkdir(outputDir, {recursive: true});

	await Promise.all(
		files.map(async (file) => {
			const content = await downloadFile(file.downloadUrl);
			const outputFile = path.join(outputDir, file.name);
			await fs.writeFile(outputFile, content, 'utf-8');
			console.log(`  Saved ${file.name}`);
		}),
	);
}

/**
 * Downloads the OpenAPI spec for a specific version.
 * @param {string} version - The API version (e.g., '8.8')
 * @param {SpecConfig} config - The configuration for this version
 * @returns {Promise<void>}
 */
async function downloadSpec(version, config) {
	if ('file' in config && config.file) {
		await downloadSingleFileSpec(version, /** @type {SingleFileSpecConfig} */ (config));
	} else if ('directory' in config && config.directory) {
		await downloadDirectorySpec(version, /** @type {DirectorySpecConfig} */ (config));
	} else {
		throw new Error(`Invalid config for version ${version}: must have 'file' or 'directory'`);
	}
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
	const availableVersions = Object.keys(SPECS_CONFIG).join(', ');
	console.log(`Usage: node download-specs.js [options]

Options:
  -v, --version <version>  Download only the specified version (can be used multiple times)
  -h, --help               Show this help message

Available versions: ${availableVersions}

Examples:
  node download-specs.js                    # Download all versions
  node download-specs.js --version 8.9      # Download only 8.9
  node download-specs.js -v 8.8 -v 8.9      # Download 8.8 and 8.9
`);
}

async function main() {
	const {versions: requestedVersions, help} = parseArgs();

	if (help) {
		printHelp();
		return;
	}

	const availableVersions = Object.keys(SPECS_CONFIG);
	const versionsToDownload = requestedVersions || availableVersions;

	for (const version of versionsToDownload) {
		if (!SPECS_CONFIG[version]) {
			throw new Error(`Unknown version: ${version}. Available versions: ${availableVersions.join(', ')}`);
		}
	}

	console.log('Downloading OpenAPI specs...\n');

	for (const version of versionsToDownload) {
		await downloadSpec(version, SPECS_CONFIG[version]);
	}

	console.log('\nAll specs downloaded successfully.');
}

main().catch((error) => {
	console.error('\nError:', error.message);
	process.exit(1);
});
