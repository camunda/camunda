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
	mockQueryGroupsByRoleEndpoint,
	mockQueryGroupsEndpoint,
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

// TEMPORARY: the design system's DataTable injects the trailing row-actions column with
// `header: () => null`, so its `<th>` has no discernible text and axe flags every table
// that uses `rowActions`. There is no prop to name that column, so this cannot be fixed
// here — remove the exclusion once the design system names it. Every other rule, and
// every other element, is still scanned.
const DS_ROW_ACTIONS_COLUMN_HEADER_RULE = 'empty-table-header';

// TEMPORARY: the design system's row-actions overflow menu is a Radix `DropdownMenu`,
// which (like `Dialog`) hides the rest of the page from assistive tech while open —
// including the page's own `<h1>` and the landmark region it lives in. It isn't something
// this page's markup does wrong (every other rule, and the menu's own contents, are still
// scanned). Remove once the design system either stops hiding the background for non-modal
// menus or names/lands them in a landmark.
const DS_DROPDOWN_MENU_HIDES_BACKGROUND_RULES = [
	'aria-hidden-focus',
	'landmark-one-main',
	'page-has-heading-one',
	'region',
];

const ROLES = [createRole({roleId: 'developers', name: 'Developers', description: 'The developer role'})];

test.beforeEach(async ({network, adminRolesPage}) => {
	await adminRolesPage.mockClientConfig();
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['admin']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryRolesEndpoint({successResponse: HttpResponse.json(createQueryRolesResponse({items: ROLES}))}),
		mockGetRoleEndpoint({successResponse: HttpResponse.json(ROLES[0]!)}),
		mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([{username: 'jane.doe'}]))}),
		mockQueryGroupsByRoleEndpoint({
			successResponse: HttpResponse.json(createMembersPage([{groupId: 'engineering'}])),
		}),
		mockQueryGroupsEndpoint({
			successResponse: HttpResponse.json(createMembersPage([{groupId: 'engineering', name: 'Engineering'}])),
		}),
		mockQueryUsersEndpoint({
			successResponse: HttpResponse.json(
				createMembersPage([
					createUser({username: 'jane.doe', name: 'Jane Doe', email: 'jane.doe@example.com'}),
					createUser({username: 'john.smith'}),
				]),
			),
		}),
	);
});

test('should have no accessibility violations in the populated roles page', async ({
	adminRolesPage,
	makeAxeBuilder,
}) => {
	await adminRolesPage.goto();
	await expect(adminRolesPage.cell('developers')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_ROW_ACTIONS_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the create role dialog', async ({adminRolesPage, makeAxeBuilder}) => {
	await adminRolesPage.goto();
	await adminRolesPage.createRoleButton.click();
	await expect(adminRolesPage.dialog).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the row actions menu', async ({adminRolesPage, makeAxeBuilder}) => {
	await adminRolesPage.goto();
	await adminRolesPage.rowActionsButton('developers').click();
	await expect(adminRolesPage.menuItem('Edit role')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder()
		.disableRules([DS_ROW_ACTIONS_COLUMN_HEADER_RULE, ...DS_DROPDOWN_MENU_HIDES_BACKGROUND_RULES])
		.analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the delete role dialog', async ({adminRolesPage, makeAxeBuilder}) => {
	await adminRolesPage.goto();
	await adminRolesPage.deleteRole('developers');
	await expect(adminRolesPage.alertDialog).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations on the role detail page', async ({
	adminRoleDetailPage,
	makeAxeBuilder,
}) => {
	await adminRoleDetailPage.goto('developers');
	await expect(adminRoleDetailPage.cell('jane.doe')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_ROW_ACTIONS_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations on the role groups tab', async ({
	adminRoleDetailPage,
	makeAxeBuilder,
}) => {
	await adminRoleDetailPage.goto('developers', '?tab=groups');
	await expect(adminRoleDetailPage.cell('engineering')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_ROW_ACTIONS_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the assign user dialog', async ({
	adminRoleDetailPage,
	makeAxeBuilder,
}) => {
	await adminRoleDetailPage.goto('developers');
	await adminRoleDetailPage.button('Assign user').click();
	await expect(adminRoleDetailPage.dialog).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the remove user dialog', async ({
	adminRoleDetailPage,
	makeAxeBuilder,
}) => {
	await adminRoleDetailPage.goto('developers');
	await adminRoleDetailPage.button('Remove user jane.doe').click();
	await expect(adminRoleDetailPage.alertDialog).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});
