/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import tanstackPlugin from '@tanstack/eslint-plugin-query';

/**
 * @param {{ browserFiles: string[] }} options
 * @returns {import("eslint").Linter.Config[]}
 */
function tanstackQueryConfig({browserFiles}) {
	return [
		{
			files: browserFiles,
			plugins: {'@tanstack/query': tanstackPlugin},
			rules: {...tanstackPlugin.configs.recommended.rules},
		},
	];
}

export {tanstackQueryConfig};
