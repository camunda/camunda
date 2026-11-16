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
	mockC3ClustersEndpoint,
	mockC3NotificationEventsEndpoint,
	mockC3NotificationsEndpoint,
	mockC3OrganizationsEndpoint,
	mockCurrentUserEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockLicenseEndpoint,
	mockLogoutEndpoint,
	mockQueryBatchOperationsEndpoint,
	mockSaasTokenEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	SAAS_CLUSTER_ID,
	SAAS_ORGANIZATION_ID,
	createC3Cluster,
	createC3Organization,
	createSaasUserToken,
} from '#/shared-test-modules/api-mocks/saas';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createQueryBatchOperationsResponse} from '#/shared-test-modules/api-mocks/batch-operations';

const currentUserMock = createCurrentUser();

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(currentUserMock),
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
});

test.describe('user menu', () => {
	test('should show the user menu as the last header control with the user name and email', async ({
		operateIndexPage,
		page,
	}) => {
		await operateIndexPage.goto();

		const {userMenuButton, infoMenuButton} = operateIndexPage.header;

		await test.step('render the user menu as the last header control', async () => {
			await expect(userMenuButton).toBeVisible();

			await infoMenuButton.focus();
			await page.keyboard.press('Tab');
			await expect(userMenuButton).toBeFocused();
		});

		await test.step('open the menu and show the user details', async () => {
			await operateIndexPage.header.openUserMenu();

			await expect(operateIndexPage.header.menu).toBeVisible();
			await expect(operateIndexPage.header.getMenuText(currentUserMock.displayName ?? '')).toBeVisible();
			await expect(operateIndexPage.header.getMenuText(currentUserMock.email ?? '')).toBeVisible();
		});
	});

	test('should return focus to the user menu button when the menu opened with the mouse is closed with Escape', async ({
		operateIndexPage,
		page,
	}) => {
		await operateIndexPage.goto();
		await operateIndexPage.header.openUserMenu();
		await expect(operateIndexPage.header.menu).toBeVisible();

		await page.keyboard.press('Escape');

		await expect(operateIndexPage.header.menu).toBeHidden();
		await expect(operateIndexPage.header.userMenuButton).toBeFocused();
	});
});

test.describe('logout', () => {
	test('should show a notification and redirect to the Operate login after clicking log out', async ({
		network,
		operateIndexPage,
		page,
	}) => {
		network.use(
			mockLogoutEndpoint({
				successResponse: new HttpResponse(null, {status: 204}),
			}),
		);

		await operateIndexPage.goto();
		await operateIndexPage.header.openUserMenu();

		network.use(
			mockCurrentUserEndpoint({
				successResponse: new HttpResponse(null, {status: 401}),
			}),
		);

		await operateIndexPage.header.logoutMenuItem.click();

		const logoutNotification = operateIndexPage.header.notifications.getByNotificationTitle('Log Out');
		await expect(logoutNotification).toBeVisible();
		await expect(logoutNotification).toContainText('You are being logged out...');

		await expect(page).toHaveURL('/operate/login');
	});

	test('should not offer log out when the authentication does not support it', async ({network, operateIndexPage}) => {
		network.use(
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(
					createSystemConfiguration({
						components: {active: ['operate']},
						authentication: {canLogout: false, isLoginDelegated: true, isCamundaGroupsEnabled: true},
					}),
				),
			}),
		);

		await operateIndexPage.goto();
		await operateIndexPage.header.openUserMenu();

		await expect(operateIndexPage.header.menu).toBeVisible();
		await expect(operateIndexPage.header.logoutMenuItem).toBeHidden();
	});
});

test.describe('language', () => {
	test('should translate the user menu button when the language is changed', async ({operateIndexPage, page}) => {
		await operateIndexPage.goto();
		await operateIndexPage.header.openUserMenu();

		await operateIndexPage.header.selectLanguage('Deutsch');
		await page.keyboard.press('Escape');

		await expect(operateIndexPage.header.getUserMenuButton('Einstellungen')).toBeVisible();
	});
});

test.describe('operations navigation group', () => {
	test('should follow the current page and forget a manual toggle after navigating', async ({
		network,
		operateIndexPage,
		page,
	}) => {
		network.use(
			mockQueryBatchOperationsEndpoint({successResponse: HttpResponse.json(createQueryBatchOperationsResponse())}),
		);
		const operationsGroup = page.getByRole('button', {name: 'Operations', exact: true});

		await operateIndexPage.goto();
		await expect(operationsGroup).toHaveAttribute('aria-expanded', 'false');

		await operationsGroup.hover();
		await operationsGroup.click();
		await expect(operationsGroup).toHaveAttribute('aria-expanded', 'true');

		await page.getByRole('link', {name: 'Batch Operations'}).click();
		await expect(operationsGroup).toHaveAttribute('aria-expanded', 'true');

		await page.getByRole('link', {name: 'Dashboard', exact: true}).click();
		await expect(operationsGroup).toHaveAttribute('aria-expanded', 'false');
	});
});

test.describe('SaaS', () => {
	test('should show the cluster environment badge and the context breadcrumbs', async ({network, operateIndexPage}) => {
		network.use(
			mockSaasTokenEndpoint({successResponse: HttpResponse.json(createSaasUserToken())}),
			mockC3OrganizationsEndpoint({successResponse: HttpResponse.json([createC3Organization()])}),
			mockC3ClustersEndpoint({successResponse: HttpResponse.json([createC3Cluster({labels: {camunda: ['dev']}})])}),
			mockC3NotificationsEndpoint({successResponse: HttpResponse.json([])}),
			mockC3NotificationEventsEndpoint({
				successResponse: new HttpResponse('', {headers: {'Content-Type': 'text/event-stream'}}),
			}),
		);
		await operateIndexPage.seedSaasBootConfig({organizationId: SAAS_ORGANIZATION_ID, clusterId: SAAS_CLUSTER_ID});

		await operateIndexPage.goto();

		await expect(operateIndexPage.header.environmentBadge('dev')).toBeVisible();
		await expect(operateIndexPage.header.breadcrumbs).toBeVisible();
	});
});
