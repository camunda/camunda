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
	mockCreateUserEndpoint,
	mockCurrentUserEndpoint,
	mockDeleteUserEndpoint,
	mockGetUserEndpoint,
	mockLicenseEndpoint,
	mockQueryUsersEndpoint,
	mockSystemConfigurationEndpoint,
	mockUpdateUserEndpoint,
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

test.describe('Admin users', () => {
	test('should list the users', async ({adminUsersPage}) => {
		await adminUsersPage.goto();

		await expect(adminUsersPage.heading).toBeVisible();
		await expect(adminUsersPage.cell('jane.doe')).toBeVisible();
		await expect(adminUsersPage.cell('Jane Doe')).toBeVisible();
		await expect(adminUsersPage.cell('jane.doe@example.com')).toBeVisible();
	});

	test('should filter the list by username', async ({adminUsersPage, page, network}) => {
		await adminUsersPage.goto();
		await expect(adminUsersPage.cell('jane.doe')).toBeVisible();

		network.use(
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(createQueryUsersResponse({items: [USERS[1]!]})),
			}),
		);
		await adminUsersPage.searchField.fill('john');

		await expect(page).toHaveURL(/search=john/);
		await expect(adminUsersPage.cell('john.smith')).toBeVisible();
		await expect(adminUsersPage.cell('jane.doe')).toBeHidden();
	});

	test('should reverse the username order when the column is sorted', async ({adminUsersPage, page}) => {
		await adminUsersPage.goto();
		await expect(adminUsersPage.cell('jane.doe')).toBeVisible();

		await adminUsersPage.usernameSortButton.click();

		await expect(page).toHaveURL(/sortOrder=desc/);
	});

	test('should create a user', async ({adminUsersPage, network}) => {
		network.use(
			mockCreateUserEndpoint({successResponse: HttpResponse.json(createUser({username: 'new.user'}))}),
			mockGetUserEndpoint({successResponse: HttpResponse.json(createUser({username: 'new.user'}))}),
		);

		await adminUsersPage.goto();
		await adminUsersPage.createUserButton.click();

		await adminUsersPage.dialogField('Username').fill('new.user');
		await adminUsersPage.dialogField('Password').fill('secret123');
		await adminUsersPage.dialogField('Confirm password').fill('secret123');

		network.use(
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(
					createQueryUsersResponse({items: [...USERS, createUser({username: 'new.user'})]}),
				),
			}),
		);
		await adminUsersPage.dialogButton('Create user').click();

		await expect(adminUsersPage.dialog).toBeHidden();
		await expect(adminUsersPage.cell('new.user')).toBeVisible();
	});

	test('should insert a newly created user at its sorted position, not always at the top', async ({
		adminUsersPage,
		network,
	}) => {
		// USERS is [jane.doe, john.smith] (ascending, the default sort) — "jim.doe" sorts between them.
		network.use(
			mockCreateUserEndpoint({successResponse: HttpResponse.json(createUser({username: 'jim.doe'}))}),
			mockGetUserEndpoint({successResponse: HttpResponse.json(createUser({username: 'jim.doe'}))}),
		);

		await adminUsersPage.goto();
		await adminUsersPage.createUserButton.click();

		await adminUsersPage.dialogField('Username').fill('jim.doe');
		await adminUsersPage.dialogField('Password').fill('secret123');
		await adminUsersPage.dialogField('Confirm password').fill('secret123');

		// Reflects eventual truth once the background reconciliation refetch lands, so the assertion
		// below holds regardless of whether it runs before or after that refetch resolves.
		network.use(
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(
					createQueryUsersResponse({items: [USERS[0]!, createUser({username: 'jim.doe'}), USERS[1]!]}),
				),
			}),
		);
		await adminUsersPage.dialogButton('Create user').click();

		await expect(adminUsersPage.dialog).toBeHidden();
		await expect(adminUsersPage.usernameCells).toHaveText(['jane.doe', 'jim.doe', 'john.smith']);
	});

	test('should sort a capitalized username the same way the backend does (ordinal, not locale-aware)', async ({
		adminUsersPage,
		network,
	}) => {
		// Ordinal/code-point order (matching the backend's collation) puts every uppercase letter
		// before every lowercase one, so "Zack" sorts *before* "jane.doe" here — the opposite of what
		// a locale-aware, case-insensitive comparison would do.
		network.use(
			mockCreateUserEndpoint({successResponse: HttpResponse.json(createUser({username: 'Zack'}))}),
			mockGetUserEndpoint({successResponse: HttpResponse.json(createUser({username: 'Zack'}))}),
		);

		await adminUsersPage.goto();
		await adminUsersPage.createUserButton.click();

		await adminUsersPage.dialogField('Username').fill('Zack');
		await adminUsersPage.dialogField('Password').fill('secret123');
		await adminUsersPage.dialogField('Confirm password').fill('secret123');

		network.use(
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(
					createQueryUsersResponse({items: [createUser({username: 'Zack'}), USERS[0]!, USERS[1]!]}),
				),
			}),
		);
		await adminUsersPage.dialogButton('Create user').click();

		await expect(adminUsersPage.dialog).toBeHidden();
		await expect(adminUsersPage.usernameCells).toHaveText(['Zack', 'jane.doe', 'john.smith']);
	});

	test('should insert a newly created user respecting a descending sort', async ({adminUsersPage, network, page}) => {
		network.use(
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(createQueryUsersResponse({items: [...USERS].reverse()})),
			}),
		);
		await page.goto('/admin/users?sortOrder=desc');
		await expect(adminUsersPage.usernameCells).toHaveText(['john.smith', 'jane.doe']);

		network.use(
			mockCreateUserEndpoint({successResponse: HttpResponse.json(createUser({username: 'jim.doe'}))}),
			mockGetUserEndpoint({successResponse: HttpResponse.json(createUser({username: 'jim.doe'}))}),
		);
		await adminUsersPage.createUserButton.click();
		await adminUsersPage.dialogField('Username').fill('jim.doe');
		await adminUsersPage.dialogField('Password').fill('secret123');
		await adminUsersPage.dialogField('Confirm password').fill('secret123');

		// Reflects eventual truth once the background reconciliation refetch lands, so the assertion
		// below holds regardless of whether it runs before or after that refetch resolves.
		network.use(
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(
					createQueryUsersResponse({items: [USERS[1]!, createUser({username: 'jim.doe'}), USERS[0]!]}),
				),
			}),
		);
		await adminUsersPage.dialogButton('Create user').click();

		await expect(adminUsersPage.dialog).toBeHidden();
		await expect(adminUsersPage.usernameCells).toHaveText(['john.smith', 'jim.doe', 'jane.doe']);
	});

	test('should show a newly created user immediately even while the search index still lags behind', async ({
		adminUsersPage,
		network,
	}) => {
		// Neither the list search nor the single-user read reflect the write yet for the rest of this
		// test — standing in for the search index not having caught up yet. The created row should
		// still appear immediately, from the confirmed `createUser` response alone.
		network.use(
			mockCreateUserEndpoint({successResponse: HttpResponse.json(createUser({username: 'new.user'}))}),
			mockGetUserEndpoint({successResponse: HttpResponse.json({}, {status: 404})}),
		);

		await adminUsersPage.goto();
		await adminUsersPage.createUserButton.click();

		await adminUsersPage.dialogField('Username').fill('new.user');
		await adminUsersPage.dialogField('Password').fill('secret123');
		await adminUsersPage.dialogField('Confirm password').fill('secret123');
		await adminUsersPage.dialogButton('Create user').click();

		await expect(adminUsersPage.dialog).toBeHidden();
		await expect(adminUsersPage.cell('new.user')).toBeVisible();
		await expect(adminUsersPage.cell('jane.doe')).toBeVisible();
	});

	test('should show a validation error when the passwords do not match', async ({adminUsersPage}) => {
		await adminUsersPage.goto();
		await adminUsersPage.createUserButton.click();

		await adminUsersPage.dialogField('Username').fill('new.user');
		await adminUsersPage.dialogField('Password').fill('secret123');
		await adminUsersPage.dialogField('Confirm password').fill('different');
		await adminUsersPage.dialogButton('Create user').click();

		await expect(adminUsersPage.dialog.getByText('Passwords do not match')).toBeVisible();
	});

	test('should edit a user', async ({adminUsersPage, network}) => {
		network.use(
			mockUpdateUserEndpoint({
				successResponse: HttpResponse.json({...USERS[0]!, name: 'Janet Doe'}),
			}),
			mockGetUserEndpoint({successResponse: HttpResponse.json({...USERS[0]!, name: 'Janet Doe'})}),
		);

		await adminUsersPage.goto();
		await adminUsersPage.editUser('jane.doe');
		await adminUsersPage.dialogField('Name').fill('Janet Doe');

		network.use(
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(
					createQueryUsersResponse({items: [{...USERS[0]!, name: 'Janet Doe'}, USERS[1]!]}),
				),
			}),
		);
		await adminUsersPage.dialogButton('Update user').click();

		await expect(adminUsersPage.dialog).toBeHidden();
		await expect(adminUsersPage.cell('Janet Doe')).toBeVisible();
	});

	test('should show an edited user immediately even while the search index still lags behind', async ({
		adminUsersPage,
		network,
	}) => {
		// `getUser` keeps serving the pre-edit version for the rest of this test — standing in for the
		// search index not having caught up with the update yet.
		network.use(
			mockUpdateUserEndpoint({successResponse: HttpResponse.json({...USERS[0]!, name: 'Janet Doe'})}),
			mockGetUserEndpoint({successResponse: HttpResponse.json(USERS[0]!)}),
		);

		await adminUsersPage.goto();
		await adminUsersPage.editUser('jane.doe');
		await adminUsersPage.dialogField('Name').fill('Janet Doe');
		await adminUsersPage.dialogButton('Update user').click();

		await expect(adminUsersPage.dialog).toBeHidden();
		await expect(adminUsersPage.cell('Janet Doe')).toBeVisible();
		await expect(adminUsersPage.cell('Jane Doe')).toBeHidden();
	});

	test('should delete a user', async ({adminUsersPage, network}) => {
		network.use(mockDeleteUserEndpoint({successResponse: new HttpResponse(null, {status: 204})}));

		await adminUsersPage.goto();
		await adminUsersPage.deleteUser('jane.doe');

		network.use(
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createQueryUsersResponse({items: [USERS[1]!]}))}),
			mockGetUserEndpoint({successResponse: HttpResponse.json({}, {status: 404})}),
		);
		await adminUsersPage.alertDialogButton('Delete user').click();

		await expect(adminUsersPage.alertDialog).toBeHidden();
		await expect(adminUsersPage.cell('jane.doe')).toBeHidden();
	});

	test('should remove a deleted user from the list immediately even while the search index still lags behind', async ({
		adminUsersPage,
		network,
	}) => {
		// Neither the list search nor the single-user read reflect the deletion for the rest of this
		// test — standing in for the search index not having caught up yet. The row should still
		// disappear immediately, and — crucially — stay gone rather than being re-added by a
		// reconciliation refetch that hasn't actually caught up.
		network.use(
			mockDeleteUserEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetUserEndpoint({successResponse: HttpResponse.json(USERS[0]!)}),
		);

		await adminUsersPage.goto();
		await adminUsersPage.deleteUser('jane.doe');
		await adminUsersPage.alertDialogButton('Delete user').click();

		await expect(adminUsersPage.alertDialog).toBeHidden();
		await expect(adminUsersPage.cell('jane.doe')).toBeHidden();
		await expect(adminUsersPage.cell('john.smith')).toBeVisible();
	});

	test('should open a user and show its details', async ({adminUsersPage, adminUserDetailPage, page, network}) => {
		network.use(mockGetUserEndpoint({successResponse: HttpResponse.json(USERS[0]!)}));

		await adminUsersPage.goto();
		await adminUsersPage.cell('jane.doe').click();

		await expect(page).toHaveURL('/admin/users/jane.doe');
		await expect(adminUserDetailPage.heading('jane.doe')).toBeVisible();
	});

	test('should delete a user from its detail page and return to the list', async ({
		adminUsersPage,
		adminUserDetailPage,
		page,
		network,
	}) => {
		network.use(
			mockGetUserEndpoint({successResponse: HttpResponse.json(USERS[0]!)}),
			mockDeleteUserEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);

		await adminUserDetailPage.goto('jane.doe');
		await expect(adminUserDetailPage.heading('jane.doe')).toBeVisible();

		network.use(
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createQueryUsersResponse({items: [USERS[1]!]}))}),
			mockGetUserEndpoint({successResponse: HttpResponse.json({}, {status: 404})}),
		);
		await adminUserDetailPage.deleteButton.click();
		await adminUserDetailPage.alertDialogButton('Delete user').click();

		await expect(page).toHaveURL('/admin/users');
		await expect(adminUsersPage.cell('jane.doe')).toBeHidden();
	});

	test('should not return to the deleted user on back navigation after deleting from its detail page', async ({
		adminUsersPage,
		adminUserDetailPage,
		page,
		network,
	}) => {
		network.use(
			mockGetUserEndpoint({successResponse: HttpResponse.json(USERS[0]!)}),
			mockDeleteUserEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);

		await adminUserDetailPage.goto('jane.doe');
		await expect(adminUserDetailPage.heading('jane.doe')).toBeVisible();

		network.use(
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createQueryUsersResponse({items: [USERS[1]!]}))}),
			mockGetUserEndpoint({successResponse: HttpResponse.json({}, {status: 404})}),
		);
		await adminUserDetailPage.deleteButton.click();
		await adminUserDetailPage.alertDialogButton('Delete user').click();
		await expect(page).toHaveURL('/admin/users');

		await page.goBack();

		await expect(page).toHaveURL('/admin/users');
		await expect(adminUsersPage.cell('jane.doe')).toBeHidden();
	});

	test('should report a load failure instead of an empty list', async ({adminUsersPage, network}) => {
		network.use(
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createQueryUsersResponse(), {status: 500})}),
		);

		await adminUsersPage.goto();

		await expect(adminUsersPage.loadFailureHeading).toBeVisible();
		await expect(adminUsersPage.table).toBeHidden();
	});
});
