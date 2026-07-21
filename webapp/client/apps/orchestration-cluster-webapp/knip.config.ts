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
		'src/global.d.ts',
		'src/operate/shared/Diagram/bpmn-js.d.ts',
		'src/operate/shared/DecisionViewer/dmn-js.d.ts',
		'src/shared/feature-flags.ts',
		'shared-test-modules/mock-handlers.ts',
		'src/shared/browser-storage/session-storage.ts',
		'shared-test-modules/api-mocks/process-definition-statistics.ts',
		'shared-test-modules/api-mocks/incident-statistics.ts',
		// TODO(#55735): remove when consumer migration is complete
		'src/operate/shared/utils/**',
		'src/operate/shared/TextAreaField/**',
		'src/operate/shared/AutoSubmit/**',
		'src/operate/shared/OptionalFiltersMenu/**',
		'src/operate/shared/DateRangeField/**',
		'src/operate/shared/EmptyMessage/**',
		'src/operate/shared/ErrorMessage/**',
		'src/operate/shared/StateIcon/**',
		'src/operate/shared/PanelHeader/**',
		'src/operate/shared/DiagramShell/**',
		'src/operate/shared/FiltersPanel/**',
		// TODO(#55642): remove when BatchOperation detail page is migrated
		'src/operate/shared/PaginatedSortableTable/**',
		// TODO(#56029, #56026): remove when Process Instance / Decision Instance shells are migrated
		'src/operate/shared/InstanceHeader/**',
		'src/operate/shared/CopyButton/**',
		'src/operate/shared/VisuallyHiddenH1/**',
	],
	ignoreDependencies: ['@vitest/browser'],
	typescript: {
		config: ['tsconfig.browser.json', 'tsconfig.vitest.json', 'tsconfig.node.json'],
	},
};

export default config;
