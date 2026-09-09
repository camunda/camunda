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
	mockLogoutEndpoint,
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse} from '#/shared-test-modules/api-mocks/user-tasks';

const currentUserMock = createCurrentUser();

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(currentUserMock),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
		mockQueryUserTasksEndpoint({
			successResponse: HttpResponse.json(createQueryUserTasksResponse({items: []})),
		}),
	);
});

test.describe('logout', () => {
	test('should show a notification and redirect to Tasklist login after clicking logout', async ({
		network,
		tasklistIndexPage,
		page,
	}) => {
		network.use(
			mockLogoutEndpoint({
				successResponse: new HttpResponse(null, {status: 204}),
			}),
		);

		await tasklistIndexPage.goto();
		await tasklistIndexPage.header.openUserSidebar();

		network.use(
			mockCurrentUserEndpoint({
				successResponse: new HttpResponse(null, {status: 401}),
			}),
		);

		await tasklistIndexPage.header.logoutButton.click();

		const logoutNotification = tasklistIndexPage.header.notifications.getByNotificationTitle('Log Out');
		await expect(logoutNotification).toBeVisible();
		await expect(logoutNotification).toContainText('You are being logged out...');

		await expect(page).toHaveURL('/tasklist/login');
	});
});

test.describe('network status', () => {
	test('should show a persistent notification when going offline and remove it when reconnecting', async ({
		tasklistIndexPage,
		page,
	}) => {
		await tasklistIndexPage.goto();
		await expect(tasklistIndexPage.tasksPanel).toBeVisible();

		await page.context().setOffline(true);
		await expect(tasklistIndexPage.header.offlineNotification).toBeVisible();

		await page.context().setOffline(false);
		await expect(tasklistIndexPage.header.offlineNotification).not.toBeVisible();
	});
});

test.describe('user sidebar', () => {
	test('should display user details and update header text when language is changed', async ({
		tasklistIndexPage,
		page,
	}) => {
		await tasklistIndexPage.goto();

		await test.step('render header with default English translations', async () => {
			await expect(tasklistIndexPage.header.productBreadcrumb).toHaveText('Tasklist');
			await expect(tasklistIndexPage.header.productBreadcrumb).toHaveAttribute('href', '/tasklist');
			await expect(tasklistIndexPage.header.tasksNavItem).toBeVisible();
			await expect(tasklistIndexPage.header.processesNavItem).toBeVisible();
		});

		await test.step('display user details in the sidebar', async () => {
			await tasklistIndexPage.header.openUserSidebar();

			await expect(page.getByText(currentUserMock.displayName)).toBeVisible();
			await expect(tasklistIndexPage.header.languageSelector).toBeVisible();
			await expect(page.getByRole('radio', {name: 'System'})).toBeChecked();
		});

		await test.step('update the selected theme', async () => {
			await tasklistIndexPage.header.selectTheme('Dark');

			await expect(page.getByRole('radio', {name: 'Dark'})).toBeChecked();
		});

		await test.step('update header text when language is changed', async () => {
			await tasklistIndexPage.header.selectLanguage('Deutsch');
			await expect(tasklistIndexPage.header.getLanguageOption('Deutsch')).toBeChecked();
			await tasklistIndexPage.header.closeUserSidebar();

			await expect(page.getByRole('link', {name: 'Aufgaben', exact: true})).toBeVisible();
			await expect(page.getByRole('link', {name: 'Prozesse'})).toBeVisible();
		});
	});
});

test.describe('info sidebar', () => {
	test('should show expected links in the info sidebar', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.goto();
		await tasklistIndexPage.header.openInfoSidebar();

		await expect(tasklistIndexPage.header.documentationLink).toBeVisible();
		await expect(tasklistIndexPage.header.camundaAcademyLink).toBeVisible();
		await expect(tasklistIndexPage.header.communityForumLink).toBeVisible();
		await expect(tasklistIndexPage.header.feedbackAndSupportLink).not.toBeVisible();
	});

	test('should show Feedback and Support link for paid plan users', async ({network, tasklistIndexPage}) => {
		network.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(createCurrentUser({salesPlanType: 'paid-cc'})),
			}),
		);

		await tasklistIndexPage.goto();
		await tasklistIndexPage.header.openInfoSidebar();

		await expect(tasklistIndexPage.header.feedbackAndSupportLink).toBeVisible();
	});
});
