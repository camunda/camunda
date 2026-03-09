/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import tsPlugin from '@typescript-eslint/eslint-plugin';
import importPlugin from 'eslint-plugin-import';
import tsParser from '@typescript-eslint/parser';
import globals from 'globals';

/**
 * Returns typescript ESLint config blocks for the given file globs.
 *
 * @param {{
 *   browserFiles: string[],
 *   testFiles: string[],
 *   nodeFiles: string[],
 *   tsconfigRootDir: string,
 *   tsProjects: string | string[],
 * }} options
 * @returns {import("eslint").Linter.Config[]}
 */
function typescriptConfig({browserFiles, testFiles, nodeFiles, tsconfigRootDir, tsProjects}) {
	return [
		{
			files: nodeFiles,
			languageOptions: {
				ecmaVersion: 2022,
				sourceType: 'module',
				parser: tsParser,
				globals: {
					...globals.node,
					...globals.es2022,
				},
			},
		},
		{
			files: [...browserFiles, ...testFiles],
			plugins: {
				import: importPlugin,
			},
			languageOptions: {
				ecmaVersion: 2022,
				sourceType: 'module',
				parser: tsParser,
				parserOptions: {
					ecmaFeatures: {jsx: true},
					project: tsProjects,
					tsconfigRootDir,
				},
				globals: {
					...globals.browser,
					...globals.es2022,
					React: false,
				},
			},
			rules: {
				'import/extensions': [
					'error',
					'ignorePackages',
					{
						js: 'always',
						jsx: 'always',
						ts: 'never',
						tsx: 'never',
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
			plugins: {'@typescript-eslint': tsPlugin},
			rules: {
				...tsPlugin.configs.recommended.rules,
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
		},
	];
}

export {typescriptConfig};
