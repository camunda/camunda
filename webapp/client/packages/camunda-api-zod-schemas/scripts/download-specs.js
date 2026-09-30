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

import {parseCliArgs, runCli} from './cli.js';
import {REPO_ROOT, SPECS_DIR, WORKSPACE_ROOT} from './paths.js';
import {CONFIG, getAvailableVersions, resolveVersions} from './supported-versions.js';

/** @typedef {import('./supported-versions.js').DownloadConfig} DownloadConfig */

/**
 * Raw file entry from GitHub Contents API (only the fields used here).
 * @see https://docs.github.com/en/rest/repos/contents#get-repository-content
 * @typedef {Object} GitHubContentsEntry
 * @property {string} name - File name
 * @property {'file' | 'dir' | 'symlink' | 'submodule'} type - Entry type
 * @property {string | null} download_url - Raw download URL (null for directories)
 */

/**
 * A spec file to save in `specs/<version>`.
 * @typedef {Object} SpecFile
 * @property {string} name - File name in the output directory
 * @property {() => Promise<string | Buffer>} read - Reads the file contents
 */

/**
 * Where the spec files come from: GitHub or the local repository.
 * @typedef {Object} SpecSource
 * @property {string} name - Description of the source, used in log messages
 * @property {(filePath: string) => SpecFile['read']} file - Returns the reader of a file (path relative to the repository root)
 * @property {(directoryPath: string) => Promise<SpecFile[]>} yamlFiles - Lists the YAML files of a directory (path relative to the repository root)
 */

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
 * @returns {Promise<SpecFile[]>} List of YAML files
 */
async function listGitHubYamlFiles(branch, directoryPath) {
	const url = `${GITHUB_API_BASE}/contents/${directoryPath}?ref=${branch}`;

	const response = await fetch(url, {
		headers: {
			Accept: 'application/vnd.github.v3+json',
			'User-Agent': 'camunda-api-zod-schemas',
		},
	});

	if (!response.ok) {
		if (response.status === 403 && response.headers.get('x-ratelimit-remaining') === '0') {
			const resetTime = response.headers.get('x-ratelimit-reset');
			const resetDate = resetTime ? new Date(parseInt(resetTime, 10) * 1000) : null;
			throw new Error(
				`GitHub API rate limit exceeded. ${resetDate ? `Resets at ${resetDate.toLocaleTimeString()}.` : ''}`,
			);
		}
		throw new Error(`Failed to list directory ${directoryPath}: ${response.status} ${response.statusText}`);
	}

	/** @type {GitHubContentsEntry[]} */
	const entries = await response.json();

	return entries.flatMap(({name, type, download_url: downloadUrl}) =>
		type === 'file' && name.endsWith('.yaml') && downloadUrl !== null
			? [{name, read: () => downloadFile(downloadUrl)}]
			: [],
	);
}

/**
 * @param {string} branch - The branch to download from
 * @returns {SpecSource}
 */
function gitHubSource(branch) {
	return {
		name: `${branch} on GitHub`,
		file: (filePath) => () => downloadFile(`${GITHUB_RAW_BASE}/${branch}/${filePath}`),
		yamlFiles: (directoryPath) => listGitHubYamlFiles(branch, directoryPath),
	};
}

/** @type {SpecSource} */
const localSource = {
	name: 'the local repository',
	file: (filePath) => () => fs.readFile(path.join(REPO_ROOT, filePath)),
	yamlFiles: async (directoryPath) => {
		const fileNames = (await fs.readdir(path.join(REPO_ROOT, directoryPath))).filter((name) => name.endsWith('.yaml'));
		return fileNames.map((name) => ({name, read: localSource.file(path.join(directoryPath, name))}));
	},
};

/**
 * Gets the spec of a version from the given source and saves it to `specs/<version>`.
 * A single file spec (8.8 style) is saved as `rest-api.yaml`; a directory spec (8.9 style) keeps its file names.
 * @param {string} version - The API version (e.g., '8.9')
 * @param {DownloadConfig} config - The configuration for this version
 * @param {SpecSource} source - Where to get the spec from
 * @returns {Promise<void>}
 */
async function getSpec(version, config, source) {
	const location = config.file || config.directory;

	if (!location) {
		throw new Error(`Invalid config for version ${version}: must have 'file' or 'directory'`);
	}

	console.log(`Getting ${version} spec from ${location} (${source.name})...`);

	/** @type {SpecFile[]} */
	let files;
	if (config.file) {
		files = [{name: 'rest-api.yaml', read: source.file(config.file)}];
	} else {
		files = await source.yamlFiles(location);
		console.log(`  Found ${files.length} YAML files`);
	}

	const outputDir = path.join(SPECS_DIR, version);
	await fs.mkdir(outputDir, {recursive: true});

	await Promise.all(
		files.map(async ({name, read}) => {
			await fs.writeFile(path.join(outputDir, name), await read());
			console.log(`  Saved ${name}`);
		}),
	);
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
	const {
		version: requestedVersions,
		local,
		help,
	} = parseCliArgs({local: {type: 'boolean', short: 'l', default: false}});

	if (help) {
		printHelp();
		return;
	}

	const versionsToProcess = resolveVersions(requestedVersions);
	const localVersion = local ? await getLocalVersion() : null;

	if (localVersion !== null && !CONFIG[localVersion]) {
		throw new Error(
			`The local repository version ${localVersion} is not supported. Available versions: ${getAvailableVersions().join(', ')}`,
		);
	}

	console.log(
		localVersion === null
			? 'Downloading OpenAPI specs...\n'
			: `Getting OpenAPI specs (${localVersion} from the local repository, others from GitHub)...\n`,
	);

	for (const version of versionsToProcess) {
		const {download} = CONFIG[version];
		await getSpec(version, download, version === localVersion ? localSource : gitHubSource(download.branch));
	}

	console.log('\nAll specs downloaded successfully.');
}

runCli(main);
