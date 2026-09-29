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

import {CONFIG, getAvailableVersions} from './supported-versions.js';

/** @typedef {import('./supported-versions.js').DownloadConfig} DownloadConfig */
/** @typedef {import('./supported-versions.js').SingleFileDownloadConfig} SingleFileDownloadConfig */
/** @typedef {import('./supported-versions.js').DirectoryDownloadConfig} DirectoryDownloadConfig */

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
 * @property {boolean} local - Whether to copy the spec from the local repository instead of downloading it
 * @property {boolean} help - Whether help flag was passed
 */

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PACKAGE_ROOT = path.resolve(__dirname, '..');
const SPECS_DIR = path.join(PACKAGE_ROOT, 'specs');
/** The `webapp/client` workspace root. Its `package.json` version follows the release line of the repository. */
const WORKSPACE_ROOT = path.resolve(PACKAGE_ROOT, '..', '..');
const REPO_ROOT = path.resolve(WORKSPACE_ROOT, '..', '..');

const GITHUB_RAW_BASE = 'https://raw.githubusercontent.com/camunda/camunda';
const GITHUB_API_BASE = 'https://api.github.com/repos/camunda/camunda';

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
 * @param {SingleFileDownloadConfig} config - The configuration for this version
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
 * @param {DirectoryDownloadConfig} config - The configuration for this version
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
 * @param {string} version - The API version (e.g., '8.9')
 * @param {DownloadConfig} config - The configuration for this version
 * @returns {Promise<void>}
 */
async function downloadSpec(version, config) {
	if ('file' in config && config.file) {
		await downloadSingleFileSpec(version, /** @type {SingleFileDownloadConfig} */ (config));
	} else if ('directory' in config && config.directory) {
		await downloadDirectorySpec(version, /** @type {DirectoryDownloadConfig} */ (config));
	} else {
		throw new Error(`Invalid config for version ${version}: must have 'file' or 'directory'`);
	}
}

/**
 * Reads the release line (for example `8.11`) from the version in the workspace `package.json`
 * (for example `8.11.0-SNAPSHOT`).
 * @returns {Promise<string>} The release line of the local repository
 */
async function getLocalVersion() {
	const packageJsonPath = path.join(WORKSPACE_ROOT, 'package.json');
	const {version} = JSON.parse(await fs.readFile(packageJsonPath, 'utf-8'));
	const match = typeof version === 'string' ? version.match(/^(\d+)\.(\d+)\./) : null;

	if (match === null) {
		throw new Error(`Cannot read the release line from version "${version}" in ${packageJsonPath}`);
	}

	return `${match[1]}.${match[2]}`;
}

/**
 * Copies the spec of a version from the local repository.
 * @param {string} version - The API version
 * @param {DownloadConfig} config - The configuration for this version
 * @returns {Promise<void>}
 */
async function copyLocalSpec(version, config) {
	const outputDir = path.join(SPECS_DIR, version);
	await fs.mkdir(outputDir, {recursive: true});

	if ('file' in config && config.file) {
		const source = path.join(REPO_ROOT, config.file);
		console.log(`Copying ${version} spec from ${path.relative(REPO_ROOT, source)}...`);
		await fs.copyFile(source, path.join(outputDir, 'rest-api.yaml'));
		console.log(`  Saved to ${path.relative(PACKAGE_ROOT, path.join(outputDir, 'rest-api.yaml'))}`);
		return;
	}

	if (!('directory' in config) || !config.directory) {
		throw new Error(`Invalid config for version ${version}: must have 'file' or 'directory'`);
	}

	const sourceDir = path.join(REPO_ROOT, config.directory);
	console.log(`Copying ${version} spec from ${path.relative(REPO_ROOT, sourceDir)}...`);

	const fileNames = (await fs.readdir(sourceDir)).filter((name) => name.endsWith('.yaml'));
	console.log(`  Found ${fileNames.length} YAML files`);

	await Promise.all(
		fileNames.map(async (fileName) => {
			await fs.copyFile(path.join(sourceDir, fileName), path.join(outputDir, fileName));
			console.log(`  Saved ${fileName}`);
		}),
	);
}

/**
 * Parses command line arguments.
 * @returns {ParsedArgs} Parsed arguments
 */
function parseArgs() {
	const args = process.argv.slice(2);
	/** @type {ParsedArgs} */
	const result = {versions: null, local: false, help: false};

	for (let index = 0; index < args.length; index++) {
		const arg = args[index];

		if (arg === '--help' || arg === '-h') {
			result.help = true;
		} else if (arg === '--local' || arg === '-l') {
			result.local = true;
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

/**
 * Prints usage information.
 * @returns {void}
 */
function printHelp() {
	const availableVersions = getAvailableVersions().join(', ');
	console.log(`Usage: node download-specs.js [options]
Options:
  -v, --version <version>  Download only the specified version (can be used multiple times)
  -l, --local              Copy the spec of the local repository version from the local repository instead of
                           downloading it. The version comes from the webapp/client package.json
                           (for example 8.11.0-SNAPSHOT -> 8.11). The other versions are still downloaded.
  -h, --help               Show this help message
Available versions: ${availableVersions}
Examples:
  node download-specs.js                    # Download all versions
  node download-specs.js --version 8.9      # Download only 8.9
  node download-specs.js -v 8.9 -v 8.10      # Download 8.9 and 8.10
  node download-specs.js --local            # Copy the local version, download all other versions
  node download-specs.js --local -v 8.11    # Copy only the local version (8.11)
`);
}

/**
 * Main entry point.
 * @returns {Promise<void>}
 */
async function main() {
	const {versions: requestedVersions, local, help} = parseArgs();

	if (help) {
		printHelp();
		return;
	}

	const availableVersions = getAvailableVersions();
	const versionsToProcess = requestedVersions || availableVersions;

	for (const version of versionsToProcess) {
		if (!CONFIG[version]) {
			throw new Error(`Unknown version: ${version}. Available versions: ${availableVersions.join(', ')}`);
		}
	}

	const localVersion = local ? await getLocalVersion() : null;

	if (localVersion !== null && !CONFIG[localVersion]) {
		throw new Error(
			`The local repository version ${localVersion} is not supported. Available versions: ${availableVersions.join(', ')}`,
		);
	}

	console.log(
		localVersion === null
			? 'Downloading OpenAPI specs...\n'
			: `Getting OpenAPI specs (${localVersion} from the local repository, others from GitHub)...\n`,
	);

	for (const version of versionsToProcess) {
		if (version === localVersion) {
			await copyLocalSpec(version, CONFIG[version].download);
		} else {
			await downloadSpec(version, CONFIG[version].download);
		}
	}

	console.log('\nAll specs downloaded successfully.');
}

main().catch((error) => {
	console.error('\nError:', error.message);
	process.exit(1);
});
