/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {LoginPage} from '#/pages/Login.page';
import {mockCurrentUserEndpoint} from '#/shared-test-modules/mock-handlers';

test('should match the login page snapshot', async ({network, page}) => {
	network.use(mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}));

	const loginPage = new LoginPage(page);
	await loginPage.goto();
	await expect(loginPage.submitButton).toBeVisible();

	await expect(page).toHaveScreenshot();
});
