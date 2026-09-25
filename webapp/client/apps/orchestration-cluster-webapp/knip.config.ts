/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {KnipConfig} from 'knip';

const config: KnipConfig = {
	entry: ['src/vitest-modules/vitest-carbon.setup.ts', 'src/vitest-modules/vitest-shadcn.setup.ts'],
	ignore: [
		'public/mockServiceWorker.js',
		'src/shared/feature-flags.ts',
		'shared-test-modules/mock-handlers.ts',
		'src/shared/browser-storage/session-storage.ts',
		'shared-test-modules/api-mocks/incident-statistics.ts',
		// TODO(#55735): remove when consumer migration is complete
		'src/operate/shared/utils/**',
		'src/operate/shared/FiltersPanel/**',
		'src/operate/shared/StructuredList/**',
		// TODO(#63423, #63424): remove when InstancesByProcess/IncidentsByError consume ExpandableListRow
		'src/operate/pages/Dashboard/shadcn.components/ExpandableList.tsx',
	],
	ignoreDependencies: ['@vitest/browser'],
	typescript: {
		config: ['tsconfig.browser.json', 'tsconfig.vitest.json', 'tsconfig.node.json'],
	},
};

export default config;
