/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import reactHooksPlugin from 'eslint-plugin-react-hooks';
import reactRefreshPlugin from 'eslint-plugin-react-refresh';

/**
 * @param {{ browserFiles: string[], testFiles: string[] }} options
 * @returns {import("eslint").Linter.Config[]}
 */
function reactConfig({browserFiles, testFiles}) {
	return [
		{
			files: browserFiles,
			plugins: {'react-hooks': reactHooksPlugin},
			rules: {...reactHooksPlugin.configs.recommended.rules},
		},
		{
			files: browserFiles,
			ignores: testFiles,
			plugins: {'react-refresh': reactRefreshPlugin},
			rules: {
				'react-refresh/only-export-components': ['error', {allowConstantExport: true}],
			},
		},
	];
}

export {reactConfig};
