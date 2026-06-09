/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {
	mockCurrentUserEndpoint,
	mockLicenseEndpoint,
	mockLoginEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';

test('should redirect to the initial page on success', async ({network, page, loginPage}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: new HttpResponse(null, {status: 401}),
		}),
		mockLoginEndpoint({
			successResponse: new HttpResponse(null, {status: 200}),
		}),
	);

	await loginPage.goto();
	await expect(loginPage.usernameInput).toBeVisible();

	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser()),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
	);

	await loginPage.fillCredentials('demo', 'demo');
	await loginPage.submitButton.click();

	await expect(page).toHaveURL('/operate');
});

test('should redirect to the referrer page', async ({network, page, loginPage}) => {
	const systemConfigurationMock = createSystemConfiguration({components: {active: ['operate']}});
	const licenseMock = createLicense();

	network.use(
		mockCurrentUserEndpoint({
			successResponse: new HttpResponse(null, {status: 401}),
		}),
		mockLoginEndpoint({
			successResponse: new HttpResponse(null, {status: 200}),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(systemConfigurationMock),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(licenseMock),
		}),
	);

	await page.goto('/operate');
	await expect(loginPage.usernameInput).toBeVisible();

	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser()),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(systemConfigurationMock),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(licenseMock),
		}),
	);

	await loginPage.fillCredentials('demo', 'demo');
	await loginPage.submitButton.click();

	await expect(page).toHaveURL('/operate');
});

test('should show an error for wrong credentials', async ({network, loginPage}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: new HttpResponse(null, {status: 401}),
		}),
		mockLoginEndpoint({
			successResponse: new HttpResponse(null, {status: 401}),
		}),
	);

	await loginPage.goto();
	await loginPage.fillCredentials('demo', 'wrong-password');
	await loginPage.submitButton.click();

	await expect(loginPage.errorMessage).toContainText(/username and password do not match/i);
});

test('should show a generic error message', async ({network, loginPage}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: new HttpResponse(null, {status: 401}),
		}),
		mockLoginEndpoint({
			successResponse: new HttpResponse(null, {status: 500}),
		}),
	);

	await loginPage.goto();
	await loginPage.fillCredentials('demo', 'demo');
	await loginPage.submitButton.click();

	await expect(loginPage.errorMessage).toContainText(/credentials could not be verified/i);
});

test('should show a loading state while the login form is submitting', async ({network, loginPage}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: new HttpResponse(null, {status: 401}),
		}),
		mockLoginEndpoint({
			successResponse: new HttpResponse(null, {status: 200}),
			delay: 500,
		}),
	);

	await loginPage.goto();
	await loginPage.fillCredentials('demo', 'demo');
	await loginPage.submitButton.click();

	await expect(loginPage.loadingButton).toBeVisible();
	await expect(loginPage.loadingButton).toBeDisabled();
});
