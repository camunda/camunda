/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import tsPlugin from '@typescript-eslint/eslint-plugin';
import importPlugin from 'eslint-plugin-import-x';
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
			plugins: {'import-x': importPlugin},
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
				'import-x/no-default-export': 'error',
				'import-x/exports-last': 'error',
				'import-x/group-exports': 'error',
				'import-x/extensions': [
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
				'import-x/resolver': {
					node: {
						extensions: ['.js', '.jsx', '.ts', '.tsx'],
					},
				},
			},
		},
		{
			files: ['**/__mocks__/**', '**/*.d.ts'],
			rules: {
				'import-x/no-default-export': 'off',
				'import-x/exports-last': 'off',
				'import-x/group-exports': 'off',
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
