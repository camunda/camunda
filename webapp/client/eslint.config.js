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

const files = {
	browser: ['packages/**/*.{js,jsx,mjs,cjs,ts,tsx,mts,cts}', 'apps/**/*.{js,jsx,mjs,cjs,ts,tsx,mts,cts}'],
	node: ['scripts/**/*.{js,mjs,cjs,ts,mts,cts}', 'prettier.config.js'],
};

export default defineConfig([
	{
		ignores: [
			'packages/**/dist/**/*',
			'apps/**/dist/**/*',
			'target/**/*',
			'apps/orchestration-cluster-webapp/src/modules/svg/**/*',
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
]);
