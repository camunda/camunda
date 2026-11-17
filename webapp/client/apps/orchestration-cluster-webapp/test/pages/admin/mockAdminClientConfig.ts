/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Page} from '@playwright/test';
import {createAdminClientConfigScript} from '#/shared-test-modules/api-mocks/authorizations';

// MSW's Playwright integration skips `.js` URLs as static assets, so the script is stubbed via `page.route`.
async function mockAdminClientConfig(page: Page, script = createAdminClientConfigScript()) {
	await page.route('**/admin/config.js', (route) =>
		route.fulfill({status: 200, contentType: 'text/javascript', body: script}),
	);
}

export {mockAdminClientConfig};
