/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export default {
	meta: {
		type: 'problem',
		docs: {description: 'Disallow test files under src/routes/'},
		schema: [],
		messages: {
			noTestsInRoutes:
				'Tests must not live in src/routes/. Route files should be thin wrappers without much logic: move any logic into the pod folder (src/{operate,tasklist,admin,shared}/...) and unit test it there. Routes themselves must be covered by the Playwright integration tests in test/integration/.',
		},
	},
	create(context) {
		return {
			Program: (node) => context.report({node, messageId: 'noTestsInRoutes'}),
		};
	},
};
