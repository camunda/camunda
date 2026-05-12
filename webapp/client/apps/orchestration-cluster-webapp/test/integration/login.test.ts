/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {http, HttpResponse, delay} from 'msw';
import {createEndpointMock} from '#/shared-test-modules/mock-endpoint';
import {LoginPage} from '#/pages/Login.page';

const mockLoginEndpoint = createEndpointMock({
	endpoint: '/login',
	method: 'POST',
});

const mockAboutEndpoint = createEndpointMock({
	endpoint: '/api/about',
	method: 'GET',
});

const mockCurrentUserEndpoint = createEndpointMock({
	endpoint: '/v2/authentication/me',
	method: 'GET',
});

test('should redirect to the initial page on success', async ({network, page}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
		mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 200})}),
	);

	const loginPage = new LoginPage(page);
	await loginPage.goto();
	// Wait for the login form to appear — confirms /login beforeLoad has completed with 401
	await loginPage.usernameInput.waitFor();

	// Prepend 200 so post-login navigation to / succeeds
	network.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json({})}));

	await loginPage.fillCredentials('demo', 'demo');
	await loginPage.submitButton.click();

	await expect(page).toHaveURL('/');
});

test('should redirect to the referrer page', async ({network, page}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
		mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 200})}),
		mockAboutEndpoint({successResponse: HttpResponse.json({message: 'About'})}),
	);

	const loginPage = new LoginPage(page);
	await page.goto('/about');
	await loginPage.usernameInput.waitFor();

	network.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json({})}));

	await loginPage.fillCredentials('demo', 'demo');
	await loginPage.submitButton.click();

	await expect(page).toHaveURL('/about');
});

test('should show an error for wrong credentials', async ({network, page}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
		mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
	);

	const loginPage = new LoginPage(page);
	await loginPage.goto();
	await loginPage.fillCredentials('demo', 'wrong-password');
	await loginPage.submitButton.click();

	await expect(page.getByText(/username and password do not match/i)).toBeVisible();
});

test('should show a generic error message', async ({network, page}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
		mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 500})}),
	);

	const loginPage = new LoginPage(page);
	await loginPage.goto();
	await loginPage.fillCredentials('demo', 'demo');
	await loginPage.submitButton.click();

	await expect(page.getByText(/credentials could not be verified/i)).toBeVisible();
});

test('should show a loading state while the login form is submitting', async ({network, page}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
		http.post('/login', async () => {
			await delay(500);
			return new HttpResponse(null, {status: 200});
		}),
	);

	const loginPage = new LoginPage(page);
	await loginPage.goto();
	await loginPage.fillCredentials('demo', 'demo');
	await loginPage.submitButton.click();

	await expect(page.getByRole('button', {name: /logging in/i})).toBeVisible();
	await expect(page.getByRole('button', {name: /logging in/i})).toBeDisabled();
});
