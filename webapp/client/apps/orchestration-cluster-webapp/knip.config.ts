/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {KnipConfig} from 'knip';

const config: KnipConfig = {
	entry: ['src/vitest-modules/vitest.setup.ts'],
	ignore: [
		'src/shared/feature-flags.ts',
		'shared-test-modules/mock-handlers.ts',
		'src/shared/browser-storage/session-storage.ts',
		'src/shared/http/request.ts',
		'shared-test-modules/api-mocks/user-tasks.ts',
	],
	ignoreDependencies: ['@vitest/browser'],
};

export default config;
