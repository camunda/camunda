/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {mockCurrentUserEndpoint} from '#/shared-test-modules/mock-handlers';

test.beforeEach(({network}) => {
	network.use(mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}));
});

test('should match the Admin setup page snapshot', async ({page, adminSetupPage}) => {
	await adminSetupPage.goto();
	await expect(adminSetupPage.submitButton).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the Admin setup page validation error state', async ({page, adminSetupPage}) => {
	await adminSetupPage.goto();
	await adminSetupPage.submitButton.click();
	await expect(adminSetupPage.usernameError).toBeVisible();

	await expect(page).toHaveScreenshot();
});
