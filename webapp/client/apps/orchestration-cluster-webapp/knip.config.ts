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
		'shared-test-modules/api-mocks/process-definitions.ts',
		'shared-test-modules/api-mocks/user-tasks.ts',
		'shared-test-modules/api-mocks/process-definition-statistics.ts',
		'shared-test-modules/api-mocks/incident-statistics.ts',
		'src/operate/shared/utils/**',
		'src/operate/shared/PanelTitle/**',
		'src/operate/shared/EmptyMessage/**',
		'src/operate/shared/ErrorMessage/**',
		'src/operate/shared/StateIcon/**',
		'src/operate/shared/PanelHeader/**',
		'src/operate/shared/DiagramShell/**',
		'src/operate/shared/Frame/**',
		'src/operate/shared/CollapsablePanel/**',
		'src/operate/shared/FiltersPanel/**',
	],
	ignoreDependencies: ['@vitest/browser', '@devbookhq/splitter'],
};

export default config;
