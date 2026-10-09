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
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockGetUserTaskEndpoint,
	mockLicenseEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse} from '#/shared-test-modules/api-mocks/user-tasks';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';

test.describe('component routes', () => {
	test('should render Operate when component is active', async ({network, page}) => {
		network.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['operate']})),
			}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
			}),
			mockLicenseEndpoint({
				successResponse: HttpResponse.json(createLicense()),
			}),
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse()),
			}),
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse()),
			}),
		);

		await page.goto('/operate');

		await expect(page.getByRole('heading', {name: 'Dashboard'})).toBeVisible();
	});

	test('should render Tasklist when component is active', async ({network, tasklistIndexPage}) => {
		network.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['tasklist']})),
			}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
			}),
			mockLicenseEndpoint({
				successResponse: HttpResponse.json(createLicense()),
			}),
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(createQueryUserTasksResponse()),
			}),
		);

		await tasklistIndexPage.goto();

		await expect(tasklistIndexPage.filterSelect).toHaveText('All open tasks');
	});

	test('should render Admin when component is active', async ({network, page}) => {
		network.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['admin']})),
			}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['admin']}})),
			}),
			mockLicenseEndpoint({
				successResponse: HttpResponse.json(createLicense()),
			}),
		);

		await page.goto('/admin');

		await expect(page.getByRole('heading', {name: 'Admin'})).toBeVisible();
	});

	test('should show the forbidden page at the original URL when component access is denied', async ({
		network,
		page,
		componentAccessDeniedPage,
	}) => {
		network.use(
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(
					createSystemConfiguration({components: {active: ['tasklist', 'operate', 'admin']}}),
				),
			}),
			mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		);

		await test.step('Tasklist', async () => {
			network.use(
				mockCurrentUserEndpoint({
					successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['operate', 'admin']})),
				}),
			);

			await page.goto('/tasklist');

			await expect(page).toHaveURL('/tasklist');
			await expect(componentAccessDeniedPage.heading).toBeVisible();
			await expect(componentAccessDeniedPage.description).toBeVisible();
			await expect(componentAccessDeniedPage.documentationLink).toBeVisible();
		});

		await test.step('Operate', async () => {
			network.use(
				mockCurrentUserEndpoint({
					successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['tasklist', 'admin']})),
				}),
			);

			await page.goto('/operate');

			await expect(page).toHaveURL('/operate');
			await expect(componentAccessDeniedPage.heading).toBeVisible();
			await expect(componentAccessDeniedPage.description).toBeVisible();
			await expect(componentAccessDeniedPage.documentationLink).toBeVisible();
		});

		await test.step('Admin', async () => {
			network.use(
				mockCurrentUserEndpoint({
					successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['tasklist', 'operate']})),
				}),
			);

			await page.goto('/admin');

			await expect(page).toHaveURL('/admin');
			await expect(componentAccessDeniedPage.heading).toBeVisible();
			await expect(componentAccessDeniedPage.description).toBeVisible();
			await expect(componentAccessDeniedPage.documentationLink).toBeVisible();
		});
	});

	test('should show error page when Tasklist is not active', async ({network, page, forbiddenPage}) => {
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(createSystemConfiguration()),
			}),
			mockLicenseEndpoint({
				successResponse: HttpResponse.json(createLicense()),
			}),
		);

		await page.goto('/tasklist');

		await expect(forbiddenPage.heading).toBeVisible();
		await expect(forbiddenPage.description).toBeVisible();
	});

	test('should show error page when Admin is not active', async ({network, page, forbiddenPage}) => {
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(createSystemConfiguration()),
			}),
			mockLicenseEndpoint({
				successResponse: HttpResponse.json(createLicense()),
			}),
		);

		await page.goto('/admin');

		await expect(forbiddenPage.heading).toBeVisible();
		await expect(forbiddenPage.description).toBeVisible();
	});

	test('should show error page on /tasklist/processes when Tasklist is not active', async ({
		network,
		page,
		forbiddenPage,
	}) => {
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(createSystemConfiguration()),
			}),
			mockLicenseEndpoint({
				successResponse: HttpResponse.json(createLicense()),
			}),
		);

		await page.goto('/tasklist/processes');

		await expect(forbiddenPage.heading).toBeVisible();
		await expect(forbiddenPage.description).toBeVisible();
	});

	test('should redirect to login when system configuration endpoint fails', async ({network, page}) => {
		network.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(null, {status: 500}),
			}),
			mockSystemConfigurationEndpoint({
				successResponse: new HttpResponse(null, {status: 500}),
			}),
			mockLicenseEndpoint({
				successResponse: HttpResponse.json(createLicense()),
			}),
		);

		await page.goto('/operate');

		await expect(page).toHaveURL('/login?redirect=%2Foperate');
		await expect(page.getByRole('heading', {name: 'Operate'})).toBeVisible();
	});

	test('should show 404 page for unknown tasklist route', async ({network, page, notFoundPage}) => {
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
			}),
			mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(createQueryUserTasksResponse()),
			}),
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json({}, {status: 404}),
			}),
		);

		await page.goto('/tasklist/nonexistent/page');

		await expect(notFoundPage.heading).toBeVisible();
	});

	test('should style the Operate 404 page and release Carbon styles after returning to Tasklist', async ({
		network,
		page,
		notFoundPage,
		tasklistIndexPage,
	}) => {
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate', 'tasklist']}})),
			}),
			mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(createQueryUserTasksResponse()),
			}),
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse()),
			}),
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse()),
			}),
		);

		await page.goto('/operate/nonexistent');

		await expect(notFoundPage.heading).toBeVisible();
		await expect(notFoundPage.goToHomeButton).toHaveCSS('background-color', 'rgb(15, 98, 254)');
		const carbonStylesheet = page.locator('head link[rel="stylesheet"][href*="assets/index-"]');
		await expect(carbonStylesheet).toHaveCount(1);

		await notFoundPage.goToHomeButton.click();

		await expect(page).toHaveURL('/tasklist');
		await expect(tasklistIndexPage.filterSelect).toBeVisible();
		await expect(carbonStylesheet).toHaveCount(0);
	});

	test('should style the Carbon error fallback when an Operate loader fails', async ({network, page}) => {
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
			}),
			mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse()),
			}),
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse()),
			}),
		);

		await page.goto('/operate/processes');

		await expect(page.getByRole('heading', {name: 'Something went wrong'})).toBeVisible({timeout: 15000});
		await expect(page.getByRole('button', {name: 'Try again'})).toHaveCSS('background-color', 'rgb(15, 98, 254)');
		await expect(page.locator('head link[rel="stylesheet"][href*="assets/index-"]')).toHaveCount(1);
	});

	test('should surface a Carbon stylesheet loading failure instead of leaving the app blank', async ({
		network,
		page,
	}) => {
		let releaseStylesheet!: () => void;
		const heldStylesheet = new Promise<void>((resolve) => {
			releaseStylesheet = resolve;
		});
		let shouldFail = true;
		await page.route('**/assets/index-*.css', async (route) => {
			await heldStylesheet;
			if (shouldFail) {
				await route.abort();
			} else {
				await route.continue();
			}
		});
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
			}),
			mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse()),
			}),
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse()),
			}),
		);

		try {
			await page.goto('/operate', {waitUntil: 'domcontentloaded'});
			await expect(page.getByRole('status')).toHaveText('Loading...');
		} finally {
			releaseStylesheet();
		}

		await expect(page.getByRole('heading', {name: 'Something went wrong'})).toBeVisible();
		await expect(page.getByRole('main')).toHaveCSS('display', 'grid');
		await expect(page.getByRole('button', {name: 'Try again'})).toHaveCSS('cursor', 'pointer');
		await expect(page.getByRole('button', {name: 'Try again'})).toHaveCSS('border-top-style', 'solid');
		shouldFail = false;
		await page.getByRole('button', {name: 'Try again'}).click();
		await expect(page.getByRole('heading', {name: 'Dashboard'})).toBeVisible();
	});

	test('should show 404 page for unknown admin route', async ({network, page, notFoundPage}) => {
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['admin']}})),
			}),
			mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		);

		await page.goto('/admin/nonexistent');

		await expect(notFoundPage.heading).toBeVisible();
	});
});
