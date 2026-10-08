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
	mockQueryGroupsEndpoint,
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

const GROUPS = [createGroup({groupId: 'engineering', name: 'Engineering', description: 'The engineering team'})];

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['admin']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryGroupsEndpoint({successResponse: HttpResponse.json(createQueryGroupsResponse({items: GROUPS}))}),
		mockGetGroupEndpoint({successResponse: HttpResponse.json(GROUPS[0]!)}),
		mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([{username: 'jane.doe'}]))}),
		mockQueryRolesByGroupEndpoint({
			successResponse: HttpResponse.json(createPage([{roleId: 'developer', name: 'Developer'}])),
		}),
		mockQueryUsersEndpoint({
			successResponse: HttpResponse.json(
				createPage([
					createUser({username: 'jane.doe', name: 'Jane Doe', email: 'jane.doe@example.com'}),
					createUser({username: 'john.smith'}),
				]),
			),
		}),
	);
});

test('should have no accessibility violations in the populated groups page', async ({
	adminGroupsPage,
	makeAxeBuilder,
}) => {
	await adminGroupsPage.goto();
	await expect(adminGroupsPage.cell('engineering')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_ROW_ACTIONS_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the create group dialog', async ({
	adminGroupsPage,
	makeAxeBuilder,
}) => {
	await adminGroupsPage.goto();
	await adminGroupsPage.createGroupButton.click();
	await expect(adminGroupsPage.dialog).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the row actions menu', async ({
	adminGroupsPage,
	makeAxeBuilder,
	page,
}) => {
	await adminGroupsPage.goto();
	await adminGroupsPage.rowActionsButton('engineering').click();
	await expect(page.getByRole('menuitem', {name: 'Edit group'})).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder()
		.disableRules([DS_ROW_ACTIONS_COLUMN_HEADER_RULE, ...DS_DROPDOWN_MENU_HIDES_BACKGROUND_RULES])
		.analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the delete group dialog', async ({
	adminGroupsPage,
	makeAxeBuilder,
}) => {
	await adminGroupsPage.goto();
	await adminGroupsPage.deleteGroup('engineering');
	await expect(adminGroupsPage.alertDialog).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations on the group detail page', async ({
	adminGroupDetailPage,
	makeAxeBuilder,
}) => {
	await adminGroupDetailPage.goto('engineering');
	await expect(adminGroupDetailPage.cell('jane.doe')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_ROW_ACTIONS_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations on the group roles tab', async ({
	adminGroupDetailPage,
	makeAxeBuilder,
}) => {
	await adminGroupDetailPage.goto('engineering', '?tab=roles');
	await expect(adminGroupDetailPage.cell('developer')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_ROW_ACTIONS_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the assign user dialog', async ({
	adminGroupDetailPage,
	makeAxeBuilder,
}) => {
	await adminGroupDetailPage.goto('engineering');
	await adminGroupDetailPage.button('Assign user').click();
	await expect(adminGroupDetailPage.dialog).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the remove user dialog', async ({
	adminGroupDetailPage,
	makeAxeBuilder,
}) => {
	await adminGroupDetailPage.goto('engineering');
	await adminGroupDetailPage.button('Remove user jane.doe').click();
	await expect(adminGroupDetailPage.alertDialog).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});
