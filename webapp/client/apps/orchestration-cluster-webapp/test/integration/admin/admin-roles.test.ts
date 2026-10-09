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
	mockAssignGroupToRoleEndpoint,
	mockAssignUserToRoleEndpoint,
	mockCreateRoleEndpoint,
	mockCurrentUserEndpoint,
	mockDeleteRoleEndpoint,
	mockGetRoleEndpoint,
	mockLicenseEndpoint,
	mockQueryClientsByRoleEndpoint,
	mockQueryGroupsByRoleEndpoint,
	mockQueryGroupsEndpoint,
	mockQueryMappingRulesByRoleEndpoint,
	mockQueryRolesEndpoint,
	mockQueryUsersByRoleEndpoint,
	mockQueryUsersEndpoint,
	mockSystemConfigurationEndpoint,
	mockUnassignUserFromRoleEndpoint,
	mockUpdateRoleEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createMembersPage, createQueryRolesResponse, createRole} from '#/shared-test-modules/api-mocks/roles';
import {createUser} from '#/shared-test-modules/api-mocks/users';

const ROLES = [
	createRole({roleId: 'developers', name: 'Developers', description: 'The developer role'}),
	createRole({roleId: 'operators', name: 'Operators', description: 'The operator role'}),
	createRole({roleId: 'admin', name: 'Admin', description: 'The default admin role'}),
];

function mockSystemConfiguration(isLoginDelegated: boolean) {
	return mockSystemConfigurationEndpoint({
		successResponse: HttpResponse.json(
			createSystemConfiguration({
				components: {active: ['admin']},
				authentication: {canLogout: true, isLoginDelegated, isCamundaGroupsEnabled: true},
			}),
		),
	});
}

test.beforeEach(async ({network, adminRolesPage}) => {
	await adminRolesPage.mockClientConfig();
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfiguration(false),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryRolesEndpoint({successResponse: HttpResponse.json(createQueryRolesResponse({items: ROLES}))}),
	);
});

test.describe('Admin roles', () => {
	test('should list the roles', async ({adminRolesPage}) => {
		await adminRolesPage.goto();

		await expect(adminRolesPage.heading).toBeVisible();
		await expect(adminRolesPage.cell('developers')).toBeVisible();
		await expect(adminRolesPage.cell('Developers')).toBeVisible();
		await expect(adminRolesPage.cell('operators')).toBeVisible();
	});

	test('should filter the list by role ID', async ({adminRolesPage, page, network}) => {
		await adminRolesPage.goto();
		await expect(adminRolesPage.cell('developers')).toBeVisible();

		network.use(
			mockQueryRolesEndpoint({
				successResponse: HttpResponse.json(createQueryRolesResponse({items: [ROLES[1]!]})),
			}),
		);
		await adminRolesPage.searchField.fill('oper');

		await expect(page).toHaveURL(/search=oper/);
		await expect(adminRolesPage.cell('operators')).toBeVisible();
		await expect(adminRolesPage.cell('developers')).toBeHidden();
	});

	test('should reverse the role ID order when the column is sorted', async ({adminRolesPage, page}) => {
		await adminRolesPage.goto();
		await expect(adminRolesPage.cell('developers')).toBeVisible();

		await adminRolesPage.roleIdSortButton.click();

		await expect(page).toHaveURL(/sortOrder=desc/);
	});

	test('should sort by name when that column is sorted', async ({adminRolesPage, page}) => {
		await adminRolesPage.goto();
		await expect(adminRolesPage.cell('developers')).toBeVisible();

		await adminRolesPage.roleNameSortButton.click();

		await expect(page).toHaveURL(/sortField=name/);
	});

	test('should create a role', async ({adminRolesPage, network}) => {
		const created = createRole({roleId: 'support', name: 'Support'});
		network.use(
			mockCreateRoleEndpoint({successResponse: HttpResponse.json(created, {status: 201})}),
			mockGetRoleEndpoint({successResponse: HttpResponse.json(created)}),
		);

		await adminRolesPage.goto();
		await adminRolesPage.createRoleButton.click();
		await adminRolesPage.dialogField('Role ID').fill('support');
		await adminRolesPage.dialogField('Role name').fill('Support');

		network.use(
			mockQueryRolesEndpoint({
				successResponse: HttpResponse.json(createQueryRolesResponse({items: [...ROLES, created]})),
			}),
		);
		await adminRolesPage.dialogButton('Create role').click();

		await expect(adminRolesPage.dialog).toBeHidden();
		await expect(adminRolesPage.cell('support')).toBeVisible();
	});

	test('should show a newly created role immediately even while the search index still lags behind', async ({
		adminRolesPage,
		network,
	}) => {
		network.use(
			mockCreateRoleEndpoint({
				successResponse: HttpResponse.json(createRole({roleId: 'support', name: 'Support'}), {status: 201}),
			}),
			mockGetRoleEndpoint({successResponse: HttpResponse.json({}, {status: 404})}),
		);

		await adminRolesPage.goto();
		await adminRolesPage.createRoleButton.click();
		await adminRolesPage.dialogField('Role ID').fill('support');
		await adminRolesPage.dialogField('Role name').fill('Support');
		await adminRolesPage.dialogButton('Create role').click();

		await expect(adminRolesPage.dialog).toBeHidden();
		await expect(adminRolesPage.cell('support')).toBeVisible();
		await expect(adminRolesPage.cell('developers')).toBeVisible();
	});

	test('should show an error when the role ID already exists', async ({adminRolesPage, network}) => {
		network.use(
			mockCreateRoleEndpoint({
				successResponse: HttpResponse.json(
					{type: 'about:blank', title: 'ALREADY_EXISTS', status: 409, detail: 'exists', instance: '/v2/roles'},
					{status: 409},
				),
			}),
		);

		await adminRolesPage.goto();
		await adminRolesPage.createRoleButton.click();
		await adminRolesPage.dialogField('Role ID').fill('developers');
		await adminRolesPage.dialogField('Role name').fill('Developers');
		await adminRolesPage.dialogButton('Create role').click();

		await expect(adminRolesPage.dialog.getByText('A role with this ID already exists')).toBeVisible();
	});

	test('should require a role ID and name', async ({adminRolesPage}) => {
		await adminRolesPage.goto();
		await adminRolesPage.createRoleButton.click();
		await adminRolesPage.dialogButton('Create role').click();

		await expect(adminRolesPage.dialog.getByText('Role ID is required')).toBeVisible();
		await expect(adminRolesPage.dialog.getByText('Role name is required')).toBeVisible();
	});

	test('should edit a role', async ({adminRolesPage, network}) => {
		const updated = {...ROLES[0]!, name: 'Platform'};
		network.use(
			mockUpdateRoleEndpoint({successResponse: HttpResponse.json(updated)}),
			mockGetRoleEndpoint({successResponse: HttpResponse.json(updated)}),
		);

		await adminRolesPage.goto();
		await adminRolesPage.editRole('developers');
		await adminRolesPage.dialogField('Role name').fill('Platform');

		network.use(
			mockQueryRolesEndpoint({
				successResponse: HttpResponse.json(createQueryRolesResponse({items: [updated, ROLES[1]!, ROLES[2]!]})),
			}),
		);
		await adminRolesPage.dialogButton('Update role').click();

		await expect(adminRolesPage.dialog).toBeHidden();
		await expect(adminRolesPage.cell('Platform')).toBeVisible();
	});

	test('should delete a role', async ({adminRolesPage, network}) => {
		network.use(mockDeleteRoleEndpoint({successResponse: new HttpResponse(null, {status: 204})}));

		await adminRolesPage.goto();
		await adminRolesPage.deleteRole('developers');

		network.use(
			mockQueryRolesEndpoint({
				successResponse: HttpResponse.json(createQueryRolesResponse({items: [ROLES[1]!, ROLES[2]!]})),
			}),
			mockGetRoleEndpoint({successResponse: HttpResponse.json({}, {status: 404})}),
		);
		await adminRolesPage.alertDialogButton('Delete role').click();

		await expect(adminRolesPage.alertDialog).toBeHidden();
		await expect(adminRolesPage.cell('developers')).toBeHidden();
	});

	test('should remove a deleted role from the list immediately even while the search index still lags behind', async ({
		adminRolesPage,
		network,
	}) => {
		network.use(
			mockDeleteRoleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetRoleEndpoint({successResponse: HttpResponse.json(ROLES[0]!)}),
		);

		await adminRolesPage.goto();
		await adminRolesPage.deleteRole('developers');
		await adminRolesPage.alertDialogButton('Delete role').click();

		await expect(adminRolesPage.alertDialog).toBeHidden();
		await expect(adminRolesPage.cell('developers')).toBeHidden();
		await expect(adminRolesPage.cell('operators')).toBeVisible();
	});

	test('should not allow editing or deleting a default role', async ({adminRolesPage}) => {
		await adminRolesPage.goto();
		await adminRolesPage.rowActionsButton('admin').click();

		await expect(adminRolesPage.menuItem('Edit role')).toBeDisabled();
		await expect(adminRolesPage.menuItem('Delete role')).toBeDisabled();
	});

	test('should report a load failure instead of an empty list', async ({adminRolesPage, network}) => {
		network.use(
			mockQueryRolesEndpoint({successResponse: HttpResponse.json(createQueryRolesResponse(), {status: 500})}),
		);

		await adminRolesPage.goto();

		await expect(adminRolesPage.loadFailureHeading).toBeVisible();
		await expect(adminRolesPage.table).toBeHidden();
	});
});

test.describe('Admin role detail', () => {
	test.beforeEach(({network}) => {
		network.use(
			mockGetRoleEndpoint({successResponse: HttpResponse.json(ROLES[0]!)}),
			mockQueryUsersByRoleEndpoint({
				successResponse: HttpResponse.json(createMembersPage([{username: 'jane.doe'}, {username: 'john.smith'}])),
			}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(
					createMembersPage([
						createUser({username: 'jane.doe', name: 'Jane Doe', email: 'jane.doe@example.com'}),
						createUser({username: 'john.smith', name: 'John Smith', email: 'john.smith@example.com'}),
					]),
				),
			}),
			mockQueryGroupsByRoleEndpoint({
				successResponse: HttpResponse.json(createMembersPage([{groupId: 'engineering'}])),
			}),
			mockQueryGroupsEndpoint({
				successResponse: HttpResponse.json(createMembersPage([{groupId: 'engineering', name: 'Engineering'}])),
			}),
			mockQueryMappingRulesByRoleEndpoint({
				successResponse: HttpResponse.json(
					createMembersPage([
						{mappingRuleId: 'rule-1', name: 'Rule one', claimName: 'groups', claimValue: 'developers'},
					]),
				),
			}),
			mockQueryClientsByRoleEndpoint({
				successResponse: HttpResponse.json(createMembersPage([{clientId: 'my-client'}])),
			}),
		);
	});

	test('should open a role from the list and show its details and users', async ({
		adminRolesPage,
		adminRoleDetailPage,
		page,
	}) => {
		await adminRolesPage.goto();
		await adminRolesPage.cell('developers').click();

		await expect(page).toHaveURL('/admin/roles/developers');
		await expect(adminRoleDetailPage.heading('Developers')).toBeVisible();
		await expect(adminRoleDetailPage.cell('jane.doe')).toBeVisible();
		await expect(adminRoleDetailPage.cell('john.smith')).toBeVisible();
		await expect(adminRoleDetailPage.cell('Jane Doe')).toBeVisible();
		await expect(adminRoleDetailPage.cell('jane.doe@example.com')).toBeVisible();
	});

	test('should only offer users and groups when the login is not delegated', async ({adminRoleDetailPage}) => {
		await adminRoleDetailPage.goto('developers');

		await expect(adminRoleDetailPage.tabs).toHaveText(['Users', 'Groups']);
	});

	test('should also offer mapping rules and clients when the login is delegated', async ({
		adminRoleDetailPage,
		network,
	}) => {
		network.use(mockSystemConfiguration(true));

		await adminRoleDetailPage.goto('developers');

		await expect(adminRoleDetailPage.tabs).toHaveText(['Users', 'Groups', 'Mapping rules', 'Clients']);
	});

	test('should switch tabs and keep the selected tab in the URL', async ({adminRoleDetailPage, page}) => {
		await adminRoleDetailPage.goto('developers');

		await adminRoleDetailPage.tab('Groups').click();

		await expect(page).toHaveURL(/tab=groups/);
		await expect(adminRoleDetailPage.cell('engineering')).toBeVisible();
		await expect(adminRoleDetailPage.cell('Engineering')).toBeVisible();
	});

	test('should open the tab named in the URL', async ({adminRoleDetailPage, network}) => {
		network.use(mockSystemConfiguration(true));

		await adminRoleDetailPage.goto('developers', '?tab=mappingRules');

		await expect(adminRoleDetailPage.cell('rule-1')).toBeVisible();
		await expect(adminRoleDetailPage.cell('Rule one')).toBeVisible();
	});

	test('should list the clients of the role when the login is delegated', async ({adminRoleDetailPage, network}) => {
		network.use(mockSystemConfiguration(true));

		await adminRoleDetailPage.goto('developers', '?tab=clients');

		await expect(adminRoleDetailPage.cell('my-client')).toBeVisible();
	});

	test('should fall back to users when the URL names a tab that is not available', async ({adminRoleDetailPage}) => {
		await adminRoleDetailPage.goto('developers', '?tab=clients');

		await expect(adminRoleDetailPage.cell('jane.doe')).toBeVisible();
	});

	test('should assign a user to the role', async ({adminRoleDetailPage, network}) => {
		network.use(
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(createMembersPage([createUser({username: 'new.user'})])),
			}),
			mockAssignUserToRoleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);

		await adminRoleDetailPage.goto('developers');
		await adminRoleDetailPage.button('Assign user').click();
		await adminRoleDetailPage.dialogCombobox.click();
		await adminRoleDetailPage.option('new.user').click();

		network.use(
			mockQueryUsersByRoleEndpoint({
				successResponse: HttpResponse.json(
					createMembersPage([{username: 'jane.doe'}, {username: 'john.smith'}, {username: 'new.user'}]),
				),
			}),
		);
		await adminRoleDetailPage.dialogButton('Assign user').click();

		await expect(adminRoleDetailPage.dialog).toBeHidden();
		await expect(adminRoleDetailPage.cell('new.user')).toBeVisible();
	});

	test('should remove a user from the role', async ({adminRoleDetailPage, network}) => {
		network.use(mockUnassignUserFromRoleEndpoint({successResponse: new HttpResponse(null, {status: 204})}));

		await adminRoleDetailPage.goto('developers');
		await adminRoleDetailPage.button('Remove user jane.doe').click();

		network.use(
			mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([{username: 'john.smith'}]))}),
		);
		await adminRoleDetailPage.alertDialogButton('Remove user').click();

		await expect(adminRoleDetailPage.alertDialog).toBeHidden();
		await expect(adminRoleDetailPage.cell('jane.doe')).toBeHidden();
		await expect(adminRoleDetailPage.cell('john.smith')).toBeVisible();
	});

	test('should assign a group to the role', async ({adminRoleDetailPage, network}) => {
		network.use(
			mockQueryGroupsEndpoint({
				successResponse: HttpResponse.json(createMembersPage([{groupId: 'support', name: 'Support'}])),
			}),
			mockAssignGroupToRoleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);

		await adminRoleDetailPage.goto('developers', '?tab=groups');
		await adminRoleDetailPage.button('Assign group').click();
		await adminRoleDetailPage.dialogCombobox.click();
		await adminRoleDetailPage.option('support').click();

		network.use(
			mockQueryGroupsByRoleEndpoint({
				successResponse: HttpResponse.json(createMembersPage([{groupId: 'engineering'}, {groupId: 'support'}])),
			}),
			mockQueryGroupsEndpoint({
				successResponse: HttpResponse.json(
					createMembersPage([
						{groupId: 'engineering', name: 'Engineering'},
						{groupId: 'support', name: 'Support'},
					]),
				),
			}),
		);
		await adminRoleDetailPage.dialogButton('Assign group').click();

		await expect(adminRoleDetailPage.dialog).toBeHidden();
		await expect(adminRoleDetailPage.cell('support')).toBeVisible();
	});

	test('should not offer editing or deleting a default role', async ({adminRoleDetailPage, network}) => {
		network.use(mockGetRoleEndpoint({successResponse: HttpResponse.json(ROLES[2]!)}));

		await adminRoleDetailPage.goto('admin');

		await expect(adminRoleDetailPage.heading('Admin')).toBeVisible();
		await expect(adminRoleDetailPage.editButton).toBeHidden();
		await expect(adminRoleDetailPage.deleteButton).toBeHidden();
	});

	test('should delete a role from its detail page and return to the list', async ({
		adminRolesPage,
		adminRoleDetailPage,
		page,
		network,
	}) => {
		network.use(mockDeleteRoleEndpoint({successResponse: new HttpResponse(null, {status: 204})}));

		await adminRoleDetailPage.goto('developers');
		await expect(adminRoleDetailPage.heading('Developers')).toBeVisible();

		network.use(
			mockQueryRolesEndpoint({
				successResponse: HttpResponse.json(createQueryRolesResponse({items: [ROLES[1]!, ROLES[2]!]})),
			}),
			mockGetRoleEndpoint({successResponse: HttpResponse.json({}, {status: 404})}),
		);
		await adminRoleDetailPage.deleteButton.click();
		await adminRoleDetailPage.alertDialogButton('Delete role').click();

		await expect(page).toHaveURL('/admin/roles');
		await expect(adminRolesPage.cell('developers')).toBeHidden();
	});
});
