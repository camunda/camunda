/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {mockCurrentUserEndpoint, mockLoginEndpoint} from '#/shared-test-modules/mock-handlers';

test('should redirect to the initial page on success', async ({network, page, loginPage}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
		mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 200})}),
	);

	await loginPage.goto();
	await expect(loginPage.usernameInput).toBeVisible();

	network.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json({})}));

	await loginPage.fillCredentials('demo', 'demo');
	await loginPage.submitButton.click();

	await expect(page).toHaveURL('/');
});

test('should redirect to the referrer page', async ({network, page, loginPage}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
		mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 200})}),
	);

	await page.goto('/about');
	await expect(loginPage.usernameInput).toBeVisible();

	network.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json({})}));

	await loginPage.fillCredentials('demo', 'demo');
	await loginPage.submitButton.click();

	await expect(page).toHaveURL('/about');
});

test('should show an error for wrong credentials', async ({network, loginPage}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
		mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
	);

	await loginPage.goto();
	await loginPage.fillCredentials('demo', 'wrong-password');
	await loginPage.submitButton.click();

	await expect(loginPage.errorMessage).toContainText(/username and password do not match/i);
});

test('should show a generic error message', async ({network, loginPage}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
		mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 500})}),
	);

	await loginPage.goto();
	await loginPage.fillCredentials('demo', 'demo');
	await loginPage.submitButton.click();

	await expect(loginPage.errorMessage).toContainText(/credentials could not be verified/i);
});

test('should show a loading state while the login form is submitting', async ({network, loginPage}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
		mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 200}), delay: 500}),
	);

	await loginPage.goto();
	await loginPage.fillCredentials('demo', 'demo');
	await loginPage.submitButton.click();

	await expect(loginPage.loadingButton).toBeVisible();
	await expect(loginPage.loadingButton).toBeDisabled();
});
