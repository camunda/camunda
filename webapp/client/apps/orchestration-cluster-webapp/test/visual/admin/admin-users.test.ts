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
	mockGetUserEndpoint,
	mockLicenseEndpoint,
	mockQueryUsersEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUsersResponse, createUser} from '#/shared-test-modules/api-mocks/users';

const USERS = [
	createUser({username: 'jane.doe', name: 'Jane Doe', email: 'jane.doe@example.com'}),
	createUser({username: 'john.smith', name: 'John Smith', email: 'john.smith@example.com'}),
];

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['admin']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryUsersEndpoint({successResponse: HttpResponse.json(createQueryUsersResponse({items: USERS}))}),
	);
});

test('should match the users page snapshot', async ({adminUsersPage, page}) => {
	await adminUsersPage.goto();
	await expect(adminUsersPage.cell('jane.doe')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the create user dialog snapshot', async ({adminUsersPage, page}) => {
	await adminUsersPage.goto();
	await adminUsersPage.createUserButton.click();
	await expect(adminUsersPage.dialog).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the row actions menu snapshot', async ({adminUsersPage, page}) => {
	await adminUsersPage.goto();
	await adminUsersPage.rowActionsButton('jane.doe').click();
	await expect(page.getByRole('menuitem', {name: 'Edit user'})).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the edit user dialog snapshot', async ({adminUsersPage, page}) => {
	await adminUsersPage.goto();
	await adminUsersPage.editUser('jane.doe');
	await expect(adminUsersPage.dialog).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the delete user dialog snapshot', async ({adminUsersPage, page}) => {
	await adminUsersPage.goto();
	await adminUsersPage.deleteUser('jane.doe');
	await expect(adminUsersPage.alertDialog).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the user detail page snapshot', async ({adminUserDetailPage, page, network}) => {
	network.use(mockGetUserEndpoint({successResponse: HttpResponse.json(USERS[0]!)}));

	await adminUserDetailPage.goto('jane.doe');
	await expect(adminUserDetailPage.heading('jane.doe')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the empty users page snapshot', async ({adminUsersPage, page, network}) => {
	network.use(mockQueryUsersEndpoint({successResponse: HttpResponse.json(createQueryUsersResponse({items: []}))}));

	await adminUsersPage.goto();
	await expect(adminUsersPage.table.getByText('No users found.')).toBeVisible();

	await expect(page).toHaveScreenshot();
});
