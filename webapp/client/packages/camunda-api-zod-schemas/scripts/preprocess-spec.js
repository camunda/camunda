/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// @ts-check

/**
 * Rewrites the downloaded OpenAPI spec before Kubb reads it, to work around Kubb output
 * that does not match the spec. The rewrites keep the meaning of the spec:
 *
 * 1. `collapseSingleRefAllOf`: `{description, allOf: [{$ref}]}` becomes `{description, $ref}`.
 *    Kubb emits `z.unknown()` for this wrapper when the parent schema also has a top-level `allOf`
 *    (for example the `filter` property of every `*SearchQuery`). It also emits `z.object({})` when
 *    the wrapper is used as `additionalProperties` (for example `UsageMetricsResponse.tenants`).
 * 2. `copyInheritedRequiredProperties`: when a schema lists a property in `required` that it only
 *    inherits through `allOf`, Kubb emits it as optional. The property is copied into the local
 *    `properties`, so that the generated intersection makes it required.
 * 3. `removePattern`: drops the `pattern` keyword of string schemas. The frontend types only describe
 *    the shape of the API; format validation is the backend's job. Kubb would emit `.regex(...)` checks,
 *    some of them with Unicode property escapes that do not work without the `u` flag.
 * 4. `PATCHES`: targeted fixes for Kubb output issues in single schemas. Each patch explains why it is
 *    necessary. Spec errors are fixed in the spec yaml itself, not here.
 */

import fs from 'node:fs/promises';
import path from 'node:path';
import {parse, stringify} from 'yaml';

/** @typedef {Record<string, any>} SchemaNode */
/** @typedef {Map<string, SchemaNode>} SpecFiles */

const ANNOTATION_KEYS = new Set(['description', 'title', 'type', 'example', 'examples', 'deprecated']);

/**
 * @param {unknown} value
 * @returns {value is SchemaNode}
 */
function isObject(value) {
	return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/**
 * Calls `visit` for every object node in the tree, children first.
 * @param {unknown} node
 * @param {(node: SchemaNode) => void} visit
 */
function walk(node, visit) {
	if (Array.isArray(node)) {
		node.forEach((child) => walk(child, visit));
		return;
	}
	if (!isObject(node)) {
		return;
	}
	Object.values(node).forEach((child) => walk(child, visit));
	visit(node);
}

/**
 * Applies `rule` to every object node of `document`, children first.
 * @param {SchemaNode} document
 * @param {(node: SchemaNode) => boolean} rule - Rewrites the node in place and returns `true` when it changed it
 * @returns {number} The number of changed nodes
 */
function rewrite(document, rule) {
	let count = 0;
	walk(document, (node) => {
		if (rule(node)) {
			count++;
		}
	});
	return count;
}

/**
 * Rule 1: `{allOf: [{$ref}], description}` becomes `{$ref, description}`.
 * Only nodes that carry nothing but annotations next to the `allOf` are collapsed. Nodes with
 * `nullable`, `properties`, `required`, etc. keep the `allOf`, because Kubb handles them.
 * @param {SchemaNode} node
 * @returns {boolean}
 */
function collapseSingleRefAllOf(node) {
	const {allOf} = node;
	if (!Array.isArray(allOf) || allOf.length !== 1) {
		return false;
	}
	const [member] = allOf;
	if (!isObject(member) || typeof member.$ref !== 'string' || Object.keys(member).length !== 1) {
		return false;
	}
	if (!Object.keys(node).every((key) => key === 'allOf' || ANNOTATION_KEYS.has(key))) {
		return false;
	}

	delete node.allOf;
	delete node.type;
	delete node.title;
	node.$ref = member.$ref;
	return true;
}

/**
 * Rule 3: removes `pattern` from string schemas. Only string values are removed, so a property that is
 * itself named `pattern` (an object under `properties`) is left alone.
 * @param {SchemaNode} node
 * @returns {boolean}
 */
function removePattern(node) {
	if (typeof node.pattern !== 'string') {
		return false;
	}
	delete node.pattern;
	return true;
}

/**
 * Splits a `$ref` into the file name and the JSON pointer.
 * @param {string} ref
 * @param {string} currentFile
 * @returns {{file: string, pointer: string}}
 */
function splitRef(ref, currentFile) {
	const [file, pointer = ''] = ref.split('#');
	return {file: file === '' ? currentFile : file, pointer};
}

/**
 * @param {SpecFiles} files
 * @param {string} ref
 * @param {string} currentFile
 * @returns {{file: string, node: SchemaNode} | undefined}
 */
function resolveRef(files, ref, currentFile) {
	const {file, pointer} = splitRef(ref, currentFile);
	/** @type {unknown} */
	let node = files.get(file);
	for (const segment of pointer.split('/').filter(Boolean)) {
		node = isObject(node) ? node[segment] : undefined;
	}
	return isObject(node) ? {file, node} : undefined;
}

/**
 * Deep-copies a node from `fromFile` so that it can be placed in `toFile`: local refs are
 * rewritten to point to `fromFile`, and refs to `toFile` become local refs.
 * @param {unknown} node
 * @param {string} fromFile
 * @param {string} toFile
 * @returns {any}
 */
function copyWithRebasedRefs(node, fromFile, toFile) {
	const copy = structuredClone(node);
	walk(copy, (current) => {
		if (typeof current.$ref !== 'string') {
			return;
		}
		const {file, pointer} = splitRef(current.$ref, fromFile);
		current.$ref = file === toFile ? `#${pointer}` : `${file}#${pointer}`;
	});
	return copy;
}

/**
 * Finds a property in the `allOf` chain of a schema.
 * @param {SpecFiles} files
 * @param {SchemaNode} schema
 * @param {string} schemaFile
 * @param {string} propertyName
 * @returns {{file: string, property: SchemaNode} | undefined}
 */
function findInheritedProperty(files, schema, schemaFile, propertyName) {
	for (const member of schema.allOf ?? []) {
		if (!isObject(member)) {
			continue;
		}
		const resolved =
			typeof member.$ref === 'string' ? resolveRef(files, member.$ref, schemaFile) : {file: schemaFile, node: member};
		if (resolved === undefined) {
			continue;
		}
		const own = resolved.node.properties?.[propertyName];
		if (isObject(own)) {
			return {file: resolved.file, property: own};
		}
		const nested = findInheritedProperty(files, resolved.node, resolved.file, propertyName);
		if (nested !== undefined) {
			return nested;
		}
	}
	return undefined;
}

/**
 * Rule 2: copies properties that are `required` but only inherited through `allOf`.
 * @param {SpecFiles} files
 * @returns {number}
 */
function copyInheritedRequiredProperties(files) {
	let count = 0;
	for (const [fileName, document] of files) {
		const schemas = document.components?.schemas ?? {};
		for (const schema of Object.values(schemas)) {
			if (!isObject(schema) || !Array.isArray(schema.allOf) || !Array.isArray(schema.required)) {
				continue;
			}
			for (const propertyName of schema.required) {
				if (isObject(schema.properties?.[propertyName])) {
					continue;
				}
				const inherited = findInheritedProperty(files, schema, fileName, propertyName);
				if (inherited === undefined) {
					continue;
				}
				schema.type ??= 'object';
				schema.properties ??= {};
				schema.properties[propertyName] = copyWithRebasedRefs(inherited.property, inherited.file, fileName);
				count++;
			}
		}
	}
	return count;
}

/**
 * Targeted fixes for single schemas.
 * @type {Array<{file: string, schema: string, reason: string, apply: (schema: SchemaNode) => void}>}
 */
const PATCHES = [
	{
		file: 'problem-detail.yaml',
		schema: 'ProblemDetail',
		reason: '`z.url()` rejects relative references such as `/v2/jobs/activation` (the spec example).',
		apply: (schema) => {
			delete schema.properties.type.format;
			delete schema.properties.instance.format;
		},
	},
];

/**
 * @param {SpecFiles} files
 * @returns {number}
 */
function applyPatches(files) {
	let count = 0;
	for (const patch of PATCHES) {
		const schema = files.get(patch.file)?.components?.schemas?.[patch.schema];
		if (!isObject(schema)) {
			console.warn(`  Patch skipped, schema not found: ${patch.file}#${patch.schema}`);
			continue;
		}
		patch.apply(schema);
		count++;
	}
	return count;
}

/**
 * Reads every YAML file in `inputDir`, applies the rewrites, and writes the result to `outputDir`.
 * @param {string} inputDir
 * @param {string} outputDir
 * @returns {Promise<void>}
 */
async function preprocessSpec(inputDir, outputDir) {
	const fileNames = (await fs.readdir(inputDir)).filter((name) => name.endsWith('.yaml'));

	/** @type {SpecFiles} */
	const files = new Map();
	for (const fileName of fileNames) {
		files.set(fileName, parse(await fs.readFile(path.join(inputDir, fileName), 'utf-8')));
	}

	// Rule 2 runs first, so that the copied properties also get collapsed by rule 1.
	const copied = copyInheritedRequiredProperties(files);
	let collapsed = 0;
	let patterns = 0;
	for (const document of files.values()) {
		collapsed += rewrite(document, collapseSingleRefAllOf);
		patterns += rewrite(document, removePattern);
	}
	const patched = applyPatches(files);

	await fs.rm(outputDir, {recursive: true, force: true});
	await fs.mkdir(outputDir, {recursive: true});
	for (const [fileName, document] of files) {
		await fs.writeFile(path.join(outputDir, fileName), stringify(document, {lineWidth: 0}), 'utf-8');
	}

	console.log(
		`  Preprocessed spec: ${collapsed} allOf wrappers collapsed, ${copied} inherited required properties copied, ${patterns} patterns removed, ${patched} patches applied`,
	);
}

export {preprocessSpec};
