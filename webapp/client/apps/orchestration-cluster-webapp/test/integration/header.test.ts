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
import {mockSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {mockLicense} from '#/shared-test-modules/api-mocks/license';
import {mockCurrentUser, mockPaidCurrentUser} from '#/shared-test-modules/api-mocks/current-user';

test.beforeEach(({network}) => {
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
});

test.describe('logout', () => {
	test('should redirect to login page after clicking logout', async ({network, tasklistIndexPage}) => {
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

		await expect(tasklistIndexPage.page).toHaveURL(/\/login/);
	});
});

test.describe('user sidebar', () => {
	test('should display the user name', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.goto();
		await tasklistIndexPage.header.openUserSidebar();

		await expect(tasklistIndexPage.page.getByText(mockCurrentUser.displayName)).toBeVisible();
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
				successResponse: HttpResponse.json(mockPaidCurrentUser),
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

	test('should update header text when language is changed', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.goto();
		await tasklistIndexPage.header.openUserSidebar();
		await tasklistIndexPage.header.selectLanguage('Deutsch');

		await expect(tasklistIndexPage.page.getByRole('link', {name: 'Aufgaben'})).toBeVisible();
		await expect(tasklistIndexPage.page.getByRole('link', {name: 'Prozesse'})).toBeVisible();
	});
});
