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
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createMembersPage, createQueryRolesResponse, createRole} from '#/shared-test-modules/api-mocks/roles';
import {createUser} from '#/shared-test-modules/api-mocks/users';

const ROLES = [
	createRole({roleId: 'developers', name: 'Developers', description: 'The developer role'}),
	createRole({roleId: 'operators', name: 'Operators', description: 'The operator role'}),
];

test.beforeEach(async ({network, adminRolesPage}) => {
	await adminRolesPage.mockClientConfig();
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(
				createSystemConfiguration({
					components: {active: ['admin']},
					authentication: {canLogout: true, isLoginDelegated: true, isCamundaGroupsEnabled: true},
				}),
			),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryRolesEndpoint({successResponse: HttpResponse.json(createQueryRolesResponse({items: ROLES}))}),
		mockGetRoleEndpoint({successResponse: HttpResponse.json(ROLES[0]!)}),
		mockQueryUsersByRoleEndpoint({
			successResponse: HttpResponse.json(createMembersPage([{username: 'jane.doe'}, {username: 'john.smith'}])),
		}),
		mockQueryGroupsByRoleEndpoint({
			successResponse: HttpResponse.json(createMembersPage([{groupId: 'engineering'}])),
		}),
		mockQueryGroupsEndpoint({
			successResponse: HttpResponse.json(createMembersPage([{groupId: 'engineering', name: 'Engineering'}])),
		}),
		mockQueryMappingRulesByRoleEndpoint({
			successResponse: HttpResponse.json(
				createMembersPage([{mappingRuleId: 'rule-1', name: 'Rule one', claimName: 'groups', claimValue: 'developers'}]),
			),
		}),
		mockQueryClientsByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([{clientId: 'my-client'}]))}),
		mockQueryUsersEndpoint({
			successResponse: HttpResponse.json(
				createMembersPage([
					createUser({username: 'jane.doe', name: 'Jane Doe', email: 'jane.doe@example.com'}),
					createUser({username: 'new.user'}),
				]),
			),
		}),
	);
});

test('should match the roles page snapshot', async ({adminRolesPage, page}) => {
	await adminRolesPage.goto();
	await expect(adminRolesPage.cell('developers')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the create role dialog snapshot', async ({adminRolesPage, page}) => {
	await adminRolesPage.goto();
	await adminRolesPage.createRoleButton.click();
	await expect(adminRolesPage.dialog).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the row actions menu snapshot', async ({adminRolesPage, page}) => {
	await adminRolesPage.goto();
	await adminRolesPage.rowActionsButton('developers').click();
	await expect(adminRolesPage.menuItem('Edit role')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the edit role dialog snapshot', async ({adminRolesPage, page}) => {
	await adminRolesPage.goto();
	await adminRolesPage.editRole('developers');
	await expect(adminRolesPage.dialog).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the delete role dialog snapshot', async ({adminRolesPage, page}) => {
	await adminRolesPage.goto();
	await adminRolesPage.deleteRole('developers');
	await expect(adminRolesPage.alertDialog).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the empty roles page snapshot', async ({adminRolesPage, page, network}) => {
	network.use(mockQueryRolesEndpoint({successResponse: HttpResponse.json(createQueryRolesResponse({items: []}))}));

	await adminRolesPage.goto();
	await expect(adminRolesPage.table.getByText('No roles found.')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the role detail page snapshot', async ({adminRoleDetailPage, page}) => {
	await adminRoleDetailPage.goto('developers');
	await expect(adminRoleDetailPage.cell('jane.doe')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the role groups tab snapshot', async ({adminRoleDetailPage, page}) => {
	await adminRoleDetailPage.goto('developers', '?tab=groups');
	await expect(adminRoleDetailPage.cell('engineering')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the role mapping rules tab snapshot', async ({adminRoleDetailPage, page}) => {
	await adminRoleDetailPage.goto('developers', '?tab=mappingRules');
	await expect(adminRoleDetailPage.cell('rule-1')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the role clients tab snapshot', async ({adminRoleDetailPage, page}) => {
	await adminRoleDetailPage.goto('developers', '?tab=clients');
	await expect(adminRoleDetailPage.cell('my-client')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the assign user dialog snapshot', async ({adminRoleDetailPage, page}) => {
	await adminRoleDetailPage.goto('developers');
	await adminRoleDetailPage.button('Assign user').click();
	await expect(adminRoleDetailPage.dialog).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the remove user dialog snapshot', async ({adminRoleDetailPage, page}) => {
	await adminRoleDetailPage.goto('developers');
	await adminRoleDetailPage.button('Remove user jane.doe').click();
	await expect(adminRoleDetailPage.alertDialog).toBeVisible();

	await expect(page).toHaveScreenshot();
});
