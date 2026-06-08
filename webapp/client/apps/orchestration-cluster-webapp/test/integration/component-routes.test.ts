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
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {mockSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {mockLicense} from '#/shared-test-modules/api-mocks/license';
import {mockCurrentUser} from '#/shared-test-modules/api-mocks/current-user';

test.describe('component routes', () => {
	test('should render Operate when component is active', async ({network, page}) => {
		network.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(mockCurrentUser),
			}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json({
					...mockSystemConfiguration,
					components: {active: ['operate']},
				}),
			}),
			mockLicenseEndpoint({
				successResponse: HttpResponse.json(mockLicense),
			}),
		);

		await page.goto('/operate');

		await expect(page.getByRole('heading', {name: 'Operate'})).toBeVisible();
	});

	test('should render Tasklist when component is active', async ({network, page}) => {
		network.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(mockCurrentUser),
			}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json({
					...mockSystemConfiguration,
					components: {active: ['tasklist']},
				}),
			}),
			mockLicenseEndpoint({
				successResponse: HttpResponse.json(mockLicense),
			}),
		);

		await page.goto('/tasklist');

		await expect(page.getByRole('heading', {name: 'Tasklist'})).toBeVisible();
	});

	test('should render Admin when component is active', async ({network, page}) => {
		network.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(mockCurrentUser),
			}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json({
					...mockSystemConfiguration,
					components: {active: ['admin']},
				}),
			}),
			mockLicenseEndpoint({
				successResponse: HttpResponse.json(mockLicense),
			}),
		);

		await page.goto('/admin');

		await expect(page.getByRole('heading', {name: 'Admin'})).toBeVisible();
	});

	test('should show error page when Tasklist is not active', async ({network, page, forbiddenPage}) => {
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(mockCurrentUser)}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(mockSystemConfiguration),
			}),
			mockLicenseEndpoint({
				successResponse: HttpResponse.json(mockLicense),
			}),
		);

		await page.goto('/tasklist');

		await expect(forbiddenPage.heading).toBeVisible();
		await expect(forbiddenPage.description).toBeVisible();
	});

	test('should show error page when Admin is not active', async ({network, page, forbiddenPage}) => {
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(mockCurrentUser)}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(mockSystemConfiguration),
			}),
			mockLicenseEndpoint({
				successResponse: HttpResponse.json(mockLicense),
			}),
		);

		await page.goto('/admin');

		await expect(forbiddenPage.heading).toBeVisible();
		await expect(forbiddenPage.description).toBeVisible();
	});

	test('should show error page on /tasklist/processes when Tasklist is not active', async ({network, page, forbiddenPage}) => {
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(mockCurrentUser)}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(mockSystemConfiguration),
			}),
			mockLicenseEndpoint({
				successResponse: HttpResponse.json(mockLicense),
			}),
		);

		await page.goto('/tasklist/processes');

		await expect(forbiddenPage.heading).toBeVisible();
		await expect(forbiddenPage.description).toBeVisible();
	});

	test('should redirect to login when system configuration endpoint fails', async ({network, page}) => {
		network.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(mockCurrentUser),
			}),
			mockSystemConfigurationEndpoint({
				successResponse: new HttpResponse(null, {status: 500}),
			}),
			mockLicenseEndpoint({
				successResponse: HttpResponse.json(mockLicense),
			}),
		);

		await page.goto('/operate');

		await expect(page).toHaveURL('/login?redirect=%2Foperate');
	});

	test('should show 404 page for unknown tasklist route', async ({network, page, notFoundPage}) => {
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(mockCurrentUser)}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json({
					...mockSystemConfiguration,
					components: {active: ['tasklist']},
				}),
			}),
			mockLicenseEndpoint({successResponse: HttpResponse.json(mockLicense)}),
		);

		await page.goto('/tasklist/nonexistent');

		await expect(notFoundPage.heading).toBeVisible();
	});

	test('should show 404 page for unknown operate route', async ({network, page, notFoundPage}) => {
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(mockCurrentUser)}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json({
					...mockSystemConfiguration,
					components: {active: ['operate']},
				}),
			}),
			mockLicenseEndpoint({successResponse: HttpResponse.json(mockLicense)}),
		);

		await page.goto('/operate/nonexistent');

		await expect(notFoundPage.heading).toBeVisible();
	});

	test('should show 404 page for unknown admin route', async ({network, page, notFoundPage}) => {
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(mockCurrentUser)}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json({
					...mockSystemConfiguration,
					components: {active: ['admin']},
				}),
			}),
			mockLicenseEndpoint({successResponse: HttpResponse.json(mockLicense)}),
		);

		await page.goto('/admin/nonexistent');

		await expect(notFoundPage.heading).toBeVisible();
	});
});
