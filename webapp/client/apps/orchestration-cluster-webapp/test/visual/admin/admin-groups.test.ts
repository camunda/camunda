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
	mockGetGroupEndpoint,
	mockLicenseEndpoint,
	mockQueryClientsByGroupEndpoint,
	mockQueryGroupsEndpoint,
	mockQueryMappingRulesByGroupEndpoint,
	mockQueryRolesByGroupEndpoint,
	mockQueryUsersByGroupEndpoint,
	mockQueryUsersEndpoint,
	mockSystemConfigurationEndpoint,
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

test.beforeEach(({network}) => {
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
		mockQueryGroupsEndpoint({successResponse: HttpResponse.json(createQueryGroupsResponse({items: GROUPS}))}),
		mockGetGroupEndpoint({successResponse: HttpResponse.json(GROUPS[0]!)}),
		mockQueryUsersByGroupEndpoint({
			successResponse: HttpResponse.json(createPage([{username: 'jane.doe'}, {username: 'john.smith'}])),
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
		mockQueryUsersEndpoint({
			successResponse: HttpResponse.json(
				createPage([
					createUser({username: 'jane.doe', name: 'Jane Doe', email: 'jane.doe@example.com'}),
					createUser({username: 'new.user'}),
				]),
			),
		}),
	);
});

test('should match the groups page snapshot', async ({adminGroupsPage, page}) => {
	await adminGroupsPage.goto();
	await expect(adminGroupsPage.cell('engineering')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the create group dialog snapshot', async ({adminGroupsPage, page}) => {
	await adminGroupsPage.goto();
	await adminGroupsPage.createGroupButton.click();
	await expect(adminGroupsPage.dialog).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the row actions menu snapshot', async ({adminGroupsPage, page}) => {
	await adminGroupsPage.goto();
	await adminGroupsPage.rowActionsButton('engineering').click();
	await expect(page.getByRole('menuitem', {name: 'Edit group'})).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the edit group dialog snapshot', async ({adminGroupsPage, page}) => {
	await adminGroupsPage.goto();
	await adminGroupsPage.editGroup('engineering');
	await expect(adminGroupsPage.dialog).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the delete group dialog snapshot', async ({adminGroupsPage, page}) => {
	await adminGroupsPage.goto();
	await adminGroupsPage.deleteGroup('engineering');
	await expect(adminGroupsPage.alertDialog).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the empty groups page snapshot', async ({adminGroupsPage, page, network}) => {
	network.use(mockQueryGroupsEndpoint({successResponse: HttpResponse.json(createQueryGroupsResponse({items: []}))}));

	await adminGroupsPage.goto();
	await expect(adminGroupsPage.table.getByText('No groups found.')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the group detail page snapshot', async ({adminGroupDetailPage, page}) => {
	await adminGroupDetailPage.goto('engineering');
	await expect(adminGroupDetailPage.cell('jane.doe')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the group roles tab snapshot', async ({adminGroupDetailPage, page}) => {
	await adminGroupDetailPage.goto('engineering', '?tab=roles');
	await expect(adminGroupDetailPage.cell('developer')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the group mapping rules tab snapshot', async ({adminGroupDetailPage, page}) => {
	await adminGroupDetailPage.goto('engineering', '?tab=mappingRules');
	await expect(adminGroupDetailPage.cell('rule-1')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the assign user dialog snapshot', async ({adminGroupDetailPage, page}) => {
	await adminGroupDetailPage.goto('engineering');
	await adminGroupDetailPage.button('Assign user').click();
	await expect(adminGroupDetailPage.dialog).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the remove user dialog snapshot', async ({adminGroupDetailPage, page}) => {
	await adminGroupDetailPage.goto('engineering');
	await adminGroupDetailPage.button('Remove user jane.doe').click();
	await expect(adminGroupDetailPage.alertDialog).toBeVisible();

	await expect(page).toHaveScreenshot();
});
