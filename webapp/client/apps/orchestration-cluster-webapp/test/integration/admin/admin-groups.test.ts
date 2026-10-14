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
	mockAssignUserToGroupEndpoint,
	mockCreateGroupEndpoint,
	mockCurrentUserEndpoint,
	mockDeleteGroupEndpoint,
	mockGetGroupEndpoint,
	mockLicenseEndpoint,
	mockQueryClientsByGroupEndpoint,
	mockQueryGroupsEndpoint,
	mockQueryMappingRulesByGroupEndpoint,
	mockQueryRolesByGroupEndpoint,
	mockQueryRolesEndpoint,
	mockQueryUsersByGroupEndpoint,
	mockQueryUsersEndpoint,
	mockSystemConfigurationEndpoint,
	mockUnassignUserFromGroupEndpoint,
	mockUpdateGroupEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createGroup, createPage, createQueryGroupsResponse} from '#/shared-test-modules/api-mocks/groups';
import {createUser} from '#/shared-test-modules/api-mocks/users';

const GROUPS = [
	createGroup({groupId: 'engineering', name: 'Engineering', description: 'The engineering team'}),
	createGroup({groupId: 'operations', name: 'Operations', description: 'The operations team'}),
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

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfiguration(false),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryGroupsEndpoint({successResponse: HttpResponse.json(createQueryGroupsResponse({items: GROUPS}))}),
	);
});

test.describe('Admin groups', () => {
	test('should list the groups', async ({adminGroupsPage}) => {
		await adminGroupsPage.goto();

		await expect(adminGroupsPage.heading).toBeVisible();
		await expect(adminGroupsPage.cell('engineering')).toBeVisible();
		await expect(adminGroupsPage.cell('Engineering')).toBeVisible();
		await expect(adminGroupsPage.cell('operations')).toBeVisible();
	});

	test('should filter the list by group ID', async ({adminGroupsPage, page, network}) => {
		await adminGroupsPage.goto();
		await expect(adminGroupsPage.cell('engineering')).toBeVisible();

		network.use(
			mockQueryGroupsEndpoint({
				successResponse: HttpResponse.json(createQueryGroupsResponse({items: [GROUPS[1]!]})),
			}),
		);
		await adminGroupsPage.searchField.fill('oper');

		await expect(page).toHaveURL(/search=oper/);
		await expect(adminGroupsPage.cell('operations')).toBeVisible();
		await expect(adminGroupsPage.cell('engineering')).toBeHidden();
	});

	test('should reverse the group ID order when the column is sorted', async ({adminGroupsPage, page}) => {
		await adminGroupsPage.goto();
		await expect(adminGroupsPage.cell('engineering')).toBeVisible();

		await adminGroupsPage.groupIdSortButton.click();

		await expect(page).toHaveURL(/sortOrder=desc/);
	});

	test('should sort by name when that column is sorted', async ({adminGroupsPage, page}) => {
		await adminGroupsPage.goto();
		await expect(adminGroupsPage.cell('engineering')).toBeVisible();

		await adminGroupsPage.groupNameSortButton.click();

		await expect(page).toHaveURL(/sortField=name/);
	});

	test('should create a group', async ({adminGroupsPage, network}) => {
		const created = createGroup({groupId: 'support', name: 'Support'});
		network.use(
			mockCreateGroupEndpoint({successResponse: HttpResponse.json(created, {status: 201})}),
			mockGetGroupEndpoint({successResponse: HttpResponse.json(created)}),
		);

		await adminGroupsPage.goto();
		await adminGroupsPage.createGroupButton.click();
		await adminGroupsPage.dialogField('Group ID').fill('support');
		await adminGroupsPage.dialogField('Group name').fill('Support');

		network.use(
			mockQueryGroupsEndpoint({
				successResponse: HttpResponse.json(createQueryGroupsResponse({items: [...GROUPS, created]})),
			}),
		);
		await adminGroupsPage.dialogButton('Create group').click();

		await expect(adminGroupsPage.dialog).toBeHidden();
		await expect(adminGroupsPage.cell('support')).toBeVisible();
	});

	test('should show a newly created group immediately even while the search index still lags behind', async ({
		adminGroupsPage,
		network,
	}) => {
		network.use(
			mockCreateGroupEndpoint({
				successResponse: HttpResponse.json(createGroup({groupId: 'support', name: 'Support'}), {status: 201}),
			}),
			mockGetGroupEndpoint({successResponse: HttpResponse.json({}, {status: 404})}),
		);

		await adminGroupsPage.goto();
		await adminGroupsPage.createGroupButton.click();
		await adminGroupsPage.dialogField('Group ID').fill('support');
		await adminGroupsPage.dialogField('Group name').fill('Support');
		await adminGroupsPage.dialogButton('Create group').click();

		await expect(adminGroupsPage.dialog).toBeHidden();
		await expect(adminGroupsPage.cell('support')).toBeVisible();
		await expect(adminGroupsPage.cell('engineering')).toBeVisible();
	});

	test('should show an error when the group ID already exists', async ({adminGroupsPage, network}) => {
		network.use(
			mockCreateGroupEndpoint({
				successResponse: HttpResponse.json(
					{type: 'about:blank', title: 'ALREADY_EXISTS', status: 409, detail: 'exists', instance: '/v2/groups'},
					{status: 409},
				),
			}),
		);

		await adminGroupsPage.goto();
		await adminGroupsPage.createGroupButton.click();
		await adminGroupsPage.dialogField('Group ID').fill('engineering');
		await adminGroupsPage.dialogField('Group name').fill('Engineering');
		await adminGroupsPage.dialogButton('Create group').click();

		await expect(adminGroupsPage.dialog.getByText('A group with this ID already exists')).toBeVisible();
	});

	test('should require a group ID and name', async ({adminGroupsPage}) => {
		await adminGroupsPage.goto();
		await adminGroupsPage.createGroupButton.click();
		await adminGroupsPage.dialogButton('Create group').click();

		await expect(adminGroupsPage.dialog.getByText('Group ID is required')).toBeVisible();
		await expect(adminGroupsPage.dialog.getByText('Group name is required')).toBeVisible();
	});

	test('should edit a group', async ({adminGroupsPage, network}) => {
		const updated = {...GROUPS[0]!, name: 'Platform'};
		network.use(
			mockUpdateGroupEndpoint({successResponse: HttpResponse.json(updated)}),
			mockGetGroupEndpoint({successResponse: HttpResponse.json(updated)}),
		);

		await adminGroupsPage.goto();
		await adminGroupsPage.editGroup('engineering');
		await adminGroupsPage.dialogField('Group name').fill('Platform');

		network.use(
			mockQueryGroupsEndpoint({
				successResponse: HttpResponse.json(createQueryGroupsResponse({items: [updated, GROUPS[1]!]})),
			}),
		);
		await adminGroupsPage.dialogButton('Update group').click();

		await expect(adminGroupsPage.dialog).toBeHidden();
		await expect(adminGroupsPage.cell('Platform')).toBeVisible();
	});

	test('should delete a group', async ({adminGroupsPage, network}) => {
		network.use(mockDeleteGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}));

		await adminGroupsPage.goto();
		await adminGroupsPage.deleteGroup('engineering');

		network.use(
			mockQueryGroupsEndpoint({
				successResponse: HttpResponse.json(createQueryGroupsResponse({items: [GROUPS[1]!]})),
			}),
			mockGetGroupEndpoint({successResponse: HttpResponse.json({}, {status: 404})}),
		);
		await adminGroupsPage.alertDialogButton('Delete group').click();

		await expect(adminGroupsPage.alertDialog).toBeHidden();
		await expect(adminGroupsPage.cell('engineering')).toBeHidden();
	});

	test('should remove a deleted group from the list immediately even while the search index still lags behind', async ({
		adminGroupsPage,
		network,
	}) => {
		network.use(
			mockDeleteGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetGroupEndpoint({successResponse: HttpResponse.json(GROUPS[0]!)}),
		);

		await adminGroupsPage.goto();
		await adminGroupsPage.deleteGroup('engineering');
		await adminGroupsPage.alertDialogButton('Delete group').click();

		await expect(adminGroupsPage.alertDialog).toBeHidden();
		await expect(adminGroupsPage.cell('engineering')).toBeHidden();
		await expect(adminGroupsPage.cell('operations')).toBeVisible();
	});

	test('should report a load failure instead of an empty list', async ({adminGroupsPage, network}) => {
		network.use(
			mockQueryGroupsEndpoint({successResponse: HttpResponse.json(createQueryGroupsResponse(), {status: 500})}),
		);

		await adminGroupsPage.goto();

		await expect(adminGroupsPage.loadFailureHeading).toBeVisible();
		await expect(adminGroupsPage.table).toBeHidden();
	});
});

test.describe('Admin group detail', () => {
	test.beforeEach(({network}) => {
		network.use(
			mockGetGroupEndpoint({successResponse: HttpResponse.json(GROUPS[0]!)}),
			mockQueryUsersByGroupEndpoint({
				successResponse: HttpResponse.json(createPage([{username: 'jane.doe'}, {username: 'john.smith'}])),
			}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(
					createPage([
						createUser({username: 'jane.doe', name: 'Jane Doe', email: 'jane.doe@example.com'}),
						createUser({username: 'john.smith', name: 'John Smith', email: 'john.smith@example.com'}),
					]),
				),
			}),
			mockQueryRolesByGroupEndpoint({
				successResponse: HttpResponse.json(createPage([{roleId: 'developer', name: 'Developer'}])),
			}),
			mockQueryMappingRulesByGroupEndpoint({
				successResponse: HttpResponse.json(
					createPage([{mappingRuleId: 'rule-1', name: 'Rule one', claimName: 'groups', claimValue: 'engineering'}]),
				),
			}),
			mockQueryClientsByGroupEndpoint({successResponse: HttpResponse.json(createPage([{clientId: 'my-client'}]))}),
		);
	});

	test('should open a group from the list and show its details and users', async ({
		adminGroupsPage,
		adminGroupDetailPage,
		page,
	}) => {
		await adminGroupsPage.goto();
		await adminGroupsPage.cell('engineering').click();

		await expect(page).toHaveURL('/admin/groups/engineering');
		await expect(adminGroupDetailPage.heading('Engineering')).toBeVisible();
		await expect(adminGroupDetailPage.cell('jane.doe')).toBeVisible();
		await expect(adminGroupDetailPage.cell('john.smith')).toBeVisible();
		await expect(adminGroupDetailPage.cell('Jane Doe')).toBeVisible();
		await expect(adminGroupDetailPage.cell('jane.doe@example.com')).toBeVisible();
	});

	test('should only offer users and roles when the login is not delegated', async ({adminGroupDetailPage}) => {
		await adminGroupDetailPage.goto('engineering');

		await expect(adminGroupDetailPage.tabs).toHaveText(['Users', 'Roles']);
	});

	test('should also offer mapping rules and clients when the login is delegated', async ({
		adminGroupDetailPage,
		network,
	}) => {
		network.use(mockSystemConfiguration(true));

		await adminGroupDetailPage.goto('engineering');

		await expect(adminGroupDetailPage.tabs).toHaveText(['Users', 'Roles', 'Mapping rules', 'Clients']);
	});

	test('should switch tabs and keep the selected tab in the URL', async ({adminGroupDetailPage, page}) => {
		await adminGroupDetailPage.goto('engineering');

		await adminGroupDetailPage.tab('Roles').click();

		await expect(page).toHaveURL(/tab=roles/);
		await expect(adminGroupDetailPage.cell('developer')).toBeVisible();
		await expect(adminGroupDetailPage.cell('Developer')).toBeVisible();
	});

	test('should open the tab named in the URL', async ({adminGroupDetailPage, network}) => {
		network.use(mockSystemConfiguration(true));

		await adminGroupDetailPage.goto('engineering', '?tab=mappingRules');

		await expect(adminGroupDetailPage.cell('rule-1')).toBeVisible();
		await expect(adminGroupDetailPage.cell('groups')).toBeVisible();
		await expect(adminGroupDetailPage.cell('Rule one')).toBeVisible();
	});

	test('should list the clients of the group when the login is delegated', async ({adminGroupDetailPage, network}) => {
		network.use(mockSystemConfiguration(true));

		await adminGroupDetailPage.goto('engineering', '?tab=clients');

		await expect(adminGroupDetailPage.cell('my-client')).toBeVisible();
	});

	test('should fall back to users when the URL names a tab that is not available', async ({adminGroupDetailPage}) => {
		await adminGroupDetailPage.goto('engineering', '?tab=clients');

		await expect(adminGroupDetailPage.cell('jane.doe')).toBeVisible();
	});

	test('should assign a user to the group', async ({adminGroupDetailPage, network}) => {
		network.use(
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(createPage([createUser({username: 'new.user'})])),
			}),
			mockAssignUserToGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);

		await adminGroupDetailPage.goto('engineering');
		await adminGroupDetailPage.button('Assign user').click();
		await adminGroupDetailPage.dialogCombobox.click();
		await adminGroupDetailPage.option('new.user').click();

		network.use(
			mockQueryUsersByGroupEndpoint({
				successResponse: HttpResponse.json(
					createPage([{username: 'jane.doe'}, {username: 'john.smith'}, {username: 'new.user'}]),
				),
			}),
		);
		await adminGroupDetailPage.dialogButton('Assign user').click();

		await expect(adminGroupDetailPage.dialog).toBeHidden();
		await expect(adminGroupDetailPage.cell('new.user')).toBeVisible();
	});

	test('should remove a user from the group', async ({adminGroupDetailPage, network}) => {
		network.use(mockUnassignUserFromGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}));

		await adminGroupDetailPage.goto('engineering');
		await adminGroupDetailPage.button('Remove user jane.doe').click();

		network.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([{username: 'john.smith'}]))}),
		);
		await adminGroupDetailPage.alertDialogButton('Remove user').click();

		await expect(adminGroupDetailPage.alertDialog).toBeHidden();
		await expect(adminGroupDetailPage.cell('jane.doe')).toBeHidden();
		await expect(adminGroupDetailPage.cell('john.smith')).toBeVisible();
	});

	test('should assign a role to the group', async ({adminGroupDetailPage, network}) => {
		network.use(
			mockQueryRolesEndpoint({
				successResponse: HttpResponse.json(createPage([{roleId: 'viewer', name: 'Viewer'}])),
			}),
			mockAssignGroupToRoleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);

		await adminGroupDetailPage.goto('engineering', '?tab=roles');
		await adminGroupDetailPage.button('Assign role').click();
		await adminGroupDetailPage.dialogCombobox.click();
		await adminGroupDetailPage.option('viewer').click();

		network.use(
			mockQueryRolesByGroupEndpoint({
				successResponse: HttpResponse.json(
					createPage([
						{roleId: 'developer', name: 'Developer'},
						{roleId: 'viewer', name: 'Viewer'},
					]),
				),
			}),
		);
		await adminGroupDetailPage.dialogButton('Assign role').click();

		await expect(adminGroupDetailPage.dialog).toBeHidden();
		await expect(adminGroupDetailPage.cell('viewer')).toBeVisible();
	});

	test('should delete a group from its detail page and return to the list', async ({
		adminGroupsPage,
		adminGroupDetailPage,
		page,
		network,
	}) => {
		network.use(mockDeleteGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}));

		await adminGroupDetailPage.goto('engineering');
		await expect(adminGroupDetailPage.heading('Engineering')).toBeVisible();

		network.use(
			mockQueryGroupsEndpoint({
				successResponse: HttpResponse.json(createQueryGroupsResponse({items: [GROUPS[1]!]})),
			}),
			mockGetGroupEndpoint({successResponse: HttpResponse.json({}, {status: 404})}),
		);
		await adminGroupDetailPage.deleteButton.click();
		await adminGroupDetailPage.alertDialogButton('Delete group').click();

		await expect(page).toHaveURL('/admin/groups');
		await expect(adminGroupsPage.cell('engineering')).toBeHidden();
	});
});
