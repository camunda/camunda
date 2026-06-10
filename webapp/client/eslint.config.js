/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';
import {defineConfig} from 'eslint/config';
import licenseHeaderPlugin from 'eslint-plugin-license-header';
import importPlugin from 'eslint-plugin-import';
import reactHooksPlugin from 'eslint-plugin-react-hooks';
import reactRefreshPlugin from 'eslint-plugin-react-refresh';
import tanstackPlugin from '@tanstack/eslint-plugin-query';
import vitestPlugin from '@vitest/eslint-plugin';

const files = {
	browser: ['packages/**/*.{js,jsx,mjs,cjs,ts,tsx,mts,cts}', 'apps/**/*.{js,jsx,mjs,cjs,ts,tsx,mts,cts}'],
	node: ['scripts/**/*.{js,mjs,cjs,ts,mts,cts}', 'prettier.config.js'],
};

const ocPath = 'apps/orchestration-cluster-webapp';
const ocFiles = {
	browser: [`${ocPath}/src/**/*.{js,jsx,ts,tsx}`],
	test: [`${ocPath}/src/**/*.test.{ts,tsx}`, `${ocPath}/src/vitest-modules/**/*`],
};

export default defineConfig([
	{
		ignores: [
			'packages/**/dist/**/*',
			'apps/**/dist/**/*',
			'target/**/*',
			`${ocPath}/src/modules/svg/**/*`,
			'apps/**/playwright-report/**/*',
			'apps/**/test-results/**/*',
		],
	},

	{
		files: files.browser,
		plugins: {js},
		extends: ['js/recommended'],
		languageOptions: {
			globals: globals.browser,
			parserOptions: {
				tsconfigRootDir: import.meta.dirname,
			},
		},
	},
	{
		files: files.node,
		plugins: {js},
		extends: ['js/recommended'],
		languageOptions: {
			globals: globals.node,
			parserOptions: {
				tsconfigRootDir: import.meta.dirname,
			},
		},
	},
	{
		files: [...files.browser, ...files.node],
		plugins: {
			'license-header': licenseHeaderPlugin,
		},
		rules: {
			'license-header/header': ['error', './resources/license-header.js'],
		},
	},
	tseslint.configs.recommended,

	// we need to disable on this package because some values are only used for type calculation
	{
		files: ['packages/camunda-api-zod-schemas/lib/**/*.{js,mjs,cjs,ts,mts,cts}'],
		rules: {
			'@typescript-eslint/no-unused-vars': 'off',
			'@typescript-eslint/no-empty-object-type': 'off',
		},
	},

	{
		files: ocFiles.browser,
		rules: {
			'no-duplicate-imports': 'error',
			curly: 'error',
		},
	},

	{
		files: [...ocFiles.browser, ...ocFiles.test],
		plugins: {import: importPlugin},
		languageOptions: {
			ecmaVersion: 2022,
			sourceType: 'module',
			parser: tseslint.parser,
			parserOptions: {
				ecmaFeatures: {jsx: true},
				project: [`./${ocPath}/tsconfig.browser.json`, `./${ocPath}/tsconfig.vitest.json`],
				tsconfigRootDir: import.meta.dirname,
			},
			globals: {
				...globals.browser,
				...globals.es2022,
				React: false,
			},
		},
		rules: {
			'import/no-default-export': 'error',
			'import/group-exports': 'error',
			'@typescript-eslint/no-unused-vars': [
				'error',
				{
					args: 'all',
					argsIgnorePattern: '^_',
					caughtErrors: 'all',
					caughtErrorsIgnorePattern: '^_',
					destructuredArrayIgnorePattern: '^_',
					varsIgnorePattern: '^_',
					ignoreRestSiblings: true,
				},
			],
		},
		settings: {
			'import/resolver': {
				node: {
					extensions: ['.js', '.jsx', '.ts', '.tsx'],
				},
			},
		},
	},

	{
		files: [`${ocPath}/src/shared/svg/**`],
		rules: {
			'import/no-default-export': 'off',
		},
	},

	{
		files: ocFiles.browser,
		plugins: {'react-hooks': reactHooksPlugin},
		rules: {...reactHooksPlugin.configs.recommended.rules},
	},

	{
		files: ocFiles.browser,
		ignores: ocFiles.test,
		plugins: {'react-refresh': reactRefreshPlugin},
		rules: {
			'react-refresh/only-export-components': ['error', {allowConstantExport: true}],
		},
	},

	{
		files: ocFiles.test,
		...vitestPlugin.configs.recommended,
		rules: {
			...vitestPlugin.configs.recommended.rules,
			'vitest/no-mocks-import': 'warn',
			'vitest/no-standalone-expect': ['error', {additionalTestBlockFunctions: ['it', 'it.for']}],
			'vitest/consistent-each-for': [
				'error',
				{
					test: 'for',
					it: 'for',
					describe: 'each',
					suite: 'each',
				},
			],
		},
		languageOptions: {
			globals: {
				...vitestPlugin.environments.env.globals,
				...globals.node,
				...globals.es2022,
			},
		},
	},

	{
		files: ocFiles.browser,
		plugins: {'@tanstack/query': tanstackPlugin},
		rules: {...tanstackPlugin.configs.recommended.rules},
	},
]);
