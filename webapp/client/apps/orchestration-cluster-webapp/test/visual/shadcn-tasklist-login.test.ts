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

test('should match theTasklist login page snapshot', async ({network, page, shadcnTasklistLoginPage}) => {
	network.use(mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}));

	await shadcnTasklistLoginPage.goto();
	await expect(shadcnTasklistLoginPage.submitButton).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the Tasklist login page validation error state', async ({
	network,
	page,
	shadcnTasklistLoginPage,
}) => {
	network.use(mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}));

	await shadcnTasklistLoginPage.goto();
	await shadcnTasklistLoginPage.submitButton.click();
	await expect(shadcnTasklistLoginPage.usernameError).toBeVisible();
	await expect(shadcnTasklistLoginPage.passwordError).toBeVisible();

	await expect(page).toHaveScreenshot();
});
