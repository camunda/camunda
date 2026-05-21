/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {mockCurrentUserEndpoint, mockSystemConfigurationEndpoint} from '#/shared-test-modules/mock-handlers';
import {mockSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';

test.describe('component routes', () => {
	test('should render Operate when component is active', async ({network, page}) => {
		network.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json({}),
			}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json({
					...mockSystemConfiguration,
					components: {active: ['operate']},
				}),
			}),
		);

		await page.goto('/operate');

		await expect(page.getByRole('heading', {name: 'Operate'})).toBeVisible();
	});

	test('should render Tasklist when component is active', async ({network, page}) => {
		network.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json({}),
			}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json({
					...mockSystemConfiguration,
					components: {active: ['tasklist']},
				}),
			}),
		);

		await page.goto('/tasklist');

		await expect(page.getByRole('heading', {name: 'Tasklist'})).toBeVisible();
	});

	test('should render Admin when component is active', async ({network, page}) => {
		network.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json({}),
			}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json({
					...mockSystemConfiguration,
					components: {active: ['admin']},
				}),
			}),
		);

		await page.goto('/admin');

		await expect(page.getByRole('heading', {name: 'Admin'})).toBeVisible();
	});

	test('should show forbidden page when Operate is not active', async ({network, page}) => {
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json({})}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(mockSystemConfiguration),
			}),
		);

		await page.goto('/operate');

		await expect(page.getByRole('heading', {name: 'You need permission'})).toBeVisible();
		await expect(page.getByText('Please contact the owner to get access.')).toBeVisible();
	});

	test('should show forbidden page when Tasklist is not active', async ({network, page}) => {
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json({})}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(mockSystemConfiguration),
			}),
		);

		await page.goto('/tasklist');

		await expect(page.getByRole('heading', {name: 'You need permission'})).toBeVisible();
		await expect(page.getByText('Please contact the owner to get access.')).toBeVisible();
	});

	test('should show forbidden page when Admin is not active', async ({network, page}) => {
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json({})}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(mockSystemConfiguration),
			}),
		);

		await page.goto('/admin');

		await expect(page.getByRole('heading', {name: 'You need permission'})).toBeVisible();
		await expect(page.getByText('Please contact the owner to get access.')).toBeVisible();
	});

	test('should redirect to login when system configuration endpoint fails', async ({network, page}) => {
		network.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json({}),
			}),
			mockSystemConfigurationEndpoint({
				successResponse: new HttpResponse(null, {status: 500}),
			}),
		);

		await page.goto('/operate');

		await expect(page).toHaveURL('/login?redirect=%2Foperate');
	});

	test('should show the generic error page when an unexpected error occurs', async ({network, page}) => {
		network.use(mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}));

		await page.goto('/login?redirect=http://evil.com');

		await expect(page.getByRole('heading', {name: 'Something went wrong'})).toBeVisible();
		await expect(page.getByText("The page couldn't be loaded. Please try again later.")).toBeVisible();
		await expect(page.getByRole('button', {name: 'Try again'})).toBeVisible();
	});

	test('should show the 404 page for unknown routes', async ({network, page}) => {
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json({})}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(mockSystemConfiguration),
			}),
		);

		await page.goto('/nonexistent-path');

		await expect(page.getByRole('heading', {name: '404 - Page not found'})).toBeVisible();
		await expect(page.getByText("We're sorry! The requested URL you're looking for could not be found.")).toBeVisible();
		await expect(page.getByRole('link', {name: 'Go to home'})).toBeVisible();
	});
});
