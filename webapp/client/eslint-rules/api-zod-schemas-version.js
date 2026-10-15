/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const PACKAGE_NAME = '@camunda/camunda-api-zod-schemas';
const VERSIONED_SOURCE = new RegExp(`^${PACKAGE_NAME}/\\d+\\.\\d+(?=/|$)`);

/** @type {import('eslint').Rule.RuleModule} */
export default {
	meta: {
		type: 'problem',
		fixable: 'code',
		docs: {
			description: `Enforce importing ${PACKAGE_NAME} from the API version matching the current Camunda version`,
		},
		schema: [
			{
				type: 'object',
				properties: {
					version: {type: 'string', pattern: '^\\d+\\.\\d+$'},
				},
				required: ['version'],
				additionalProperties: false,
			},
		],
		messages: {
			wrongVersion: `Import from '{{expected}}' instead of '{{actual}}'. The API version must match the version in webapp/client/package.json.`,
		},
	},
	create(context) {
		const [{version}] = context.options;
		const expectedPrefix = `${PACKAGE_NAME}/${version}`;

		function check(sourceNode) {
			if (sourceNode?.type !== 'Literal' || typeof sourceNode.value !== 'string') {
				return;
			}

			const source = sourceNode.value;
			if (source !== PACKAGE_NAME && !source.startsWith(`${PACKAGE_NAME}/`)) {
				return;
			}
			if (source === expectedPrefix || source.startsWith(`${expectedPrefix}/`)) {
				return;
			}

			const fixedSource = VERSIONED_SOURCE.test(source)
				? source.replace(VERSIONED_SOURCE, expectedPrefix)
				: source.replace(PACKAGE_NAME, expectedPrefix);
			const quote = sourceNode.raw[0];

			context.report({
				node: sourceNode,
				messageId: 'wrongVersion',
				data: {expected: fixedSource, actual: source},
				fix: (fixer) => fixer.replaceText(sourceNode, `${quote}${fixedSource}${quote}`),
			});
		}

		return {
			ImportDeclaration: (node) => check(node.source),
			ExportNamedDeclaration: (node) => check(node.source),
			ExportAllDeclaration: (node) => check(node.source),
			ImportExpression: (node) => check(node.source),
			TSImportType: (node) => check(node.argument?.literal),
		};
	},
};
