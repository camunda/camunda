/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import vitestPlugin from '@vitest/eslint-plugin';
import testingLibraryPlugin from 'eslint-plugin-testing-library';
import globals from 'globals';

/**
 * @param {{ testFiles: string[] }} options
 * @returns {import("eslint").Linter.Config[]}
 */
function testingConfig({testFiles}) {
	return [
		{
			files: testFiles,
			...vitestPlugin.configs.recommended,
			rules: {
				...vitestPlugin.configs.recommended.rules,
				'vitest/no-mocks-import': 'warn',
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
			files: testFiles,
			plugins: {'testing-library': testingLibraryPlugin},
			rules: {
				...testingLibraryPlugin.configs.react.rules,
				'testing-library/no-debugging-utils': 'error',
				'testing-library/no-node-access': 'off',
			},
		},
	];
}

export {testingConfig};
