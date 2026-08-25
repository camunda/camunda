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
		shadcnTasklistIndexPage,
		page,
	}) => {
		network.use(
			mockLogoutEndpoint({
				successResponse: new HttpResponse(null, {status: 204}),
			}),
		);

		await shadcnTasklistIndexPage.goto();
		await shadcnTasklistIndexPage.header.openUserSidebar();

		network.use(
			mockCurrentUserEndpoint({
				successResponse: new HttpResponse(null, {status: 401}),
			}),
		);

		await shadcnTasklistIndexPage.header.logoutButton.click();

		const logoutNotification = shadcnTasklistIndexPage.header.notifications.getByNotificationTitle('Log Out');
		await expect(logoutNotification).toBeVisible();
		await expect(logoutNotification).toContainText('You are being logged out...');

		await expect(page).toHaveURL('/shadcn/tasklist/login');
	});
});

test.describe('user sidebar', () => {
	test('should display user details and update header text when language is changed', async ({
		shadcnTasklistIndexPage,
		page,
	}) => {
		await shadcnTasklistIndexPage.goto();

		await test.step('render header with default English translations', async () => {
			await expect(shadcnTasklistIndexPage.header.productBreadcrumb).toHaveText('Tasklist');
			await expect(shadcnTasklistIndexPage.header.productBreadcrumb).toHaveAttribute('href', '/shadcn/tasklist');
			await expect(shadcnTasklistIndexPage.header.tasksNavItem).toBeVisible();
			await expect(shadcnTasklistIndexPage.header.processesNavItem).toBeVisible();
		});

		await test.step('display user details in the sidebar', async () => {
			await shadcnTasklistIndexPage.header.openUserSidebar();

			await expect(page.getByText(currentUserMock.displayName)).toBeVisible();
			await expect(shadcnTasklistIndexPage.header.languageSelector).toBeVisible();
			await expect(page.getByRole('radio', {name: 'System'})).toBeChecked();
		});

		await test.step('update the selected theme', async () => {
			await shadcnTasklistIndexPage.header.selectTheme('Dark');

			await expect(page.getByRole('radio', {name: 'Dark'})).toBeChecked();
		});

		await test.step('update header text when language is changed', async () => {
			await shadcnTasklistIndexPage.header.selectLanguage('Deutsch');
			await expect(shadcnTasklistIndexPage.header.getLanguageOption('Deutsch')).toBeChecked();
			await shadcnTasklistIndexPage.header.closeUserSidebar();

			await expect(page.getByRole('link', {name: 'Aufgaben', exact: true})).toBeVisible();
			await expect(page.getByRole('link', {name: 'Prozesse'})).toBeVisible();
		});
	});
});

test.describe('info sidebar', () => {
	test('should show expected links in the info sidebar', async ({shadcnTasklistIndexPage}) => {
		await shadcnTasklistIndexPage.goto();
		await shadcnTasklistIndexPage.header.openInfoSidebar();

		await expect(shadcnTasklistIndexPage.header.documentationLink).toBeVisible();
		await expect(shadcnTasklistIndexPage.header.camundaAcademyLink).toBeVisible();
		await expect(shadcnTasklistIndexPage.header.communityForumLink).toBeVisible();
		await expect(shadcnTasklistIndexPage.header.feedbackAndSupportLink).not.toBeVisible();
	});

	test('should show Feedback and Support link for paid plan users', async ({network, shadcnTasklistIndexPage}) => {
		network.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(createCurrentUser({salesPlanType: 'paid-cc'})),
			}),
		);

		await shadcnTasklistIndexPage.goto();
		await shadcnTasklistIndexPage.header.openInfoSidebar();

		await expect(shadcnTasklistIndexPage.header.feedbackAndSupportLink).toBeVisible();
	});
});
