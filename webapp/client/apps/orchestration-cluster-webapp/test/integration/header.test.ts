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
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';

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
	);
});

test.describe('logout', () => {
	test('should show a notification and redirect to login page after clicking logout', async ({
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

		await expect(page).toHaveURL(/\/login/);
	});
});

test.describe('notifications', () => {
	test('should show a notification when going offline and dismiss it when reconnecting', async ({
		tasklistIndexPage,
		page,
	}) => {
		await tasklistIndexPage.goto();
		await expect(tasklistIndexPage.tasksPanelHeading('All open tasks')).toBeVisible();

		await page.context().setOffline(true);
		await expect(
			tasklistIndexPage.header.notifications.getByNotificationTitle('Internet connection lost'),
		).toBeVisible();

		await page.context().setOffline(false);
		await expect(
			tasklistIndexPage.header.notifications.getByNotificationTitle('Internet connection lost'),
		).not.toBeVisible();
	});
});

test.describe('user sidebar', () => {
	test('should display the user name', async ({tasklistIndexPage, page}) => {
		await tasklistIndexPage.goto();
		await tasklistIndexPage.header.openUserSidebar();

		await expect(page.getByText(currentUserMock.displayName)).toBeVisible();
	});

	test('should display the language selector', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.goto();
		await tasklistIndexPage.header.openUserSidebar();

		await expect(tasklistIndexPage.header.languageSelector).toBeVisible();
	});
});

test.describe('info sidebar', () => {
	test('should show Documentation link', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.goto();
		await tasklistIndexPage.header.openInfoSidebar();

		await expect(tasklistIndexPage.header.documentationLink).toBeVisible();
	});

	test('should show Camunda Academy link', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.goto();
		await tasklistIndexPage.header.openInfoSidebar();

		await expect(tasklistIndexPage.header.camundaAcademyLink).toBeVisible();
	});

	test('should show Community Forum link', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.goto();
		await tasklistIndexPage.header.openInfoSidebar();

		await expect(tasklistIndexPage.header.communityForumLink).toBeVisible();
	});

	test('should not show Feedback and Support link for non-paid users', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.goto();
		await tasklistIndexPage.header.openInfoSidebar();

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

test.describe('i18n', () => {
	test('should render header with default English translations', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.goto();

		await expect(tasklistIndexPage.tasksNavItem).toBeVisible();
		await expect(tasklistIndexPage.processesNavItem).toBeVisible();
	});

	test('should update header text when language is changed', async ({tasklistIndexPage, page}) => {
		await tasklistIndexPage.goto();
		await tasklistIndexPage.header.openUserSidebar();
		await tasklistIndexPage.header.selectLanguage('Deutsch');

		await expect(page.getByRole('link', {name: 'Aufgaben'})).toBeVisible();
		await expect(page.getByRole('link', {name: 'Prozesse'})).toBeVisible();
	});
});
