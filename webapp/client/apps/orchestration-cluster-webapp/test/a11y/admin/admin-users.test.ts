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

// TEMPORARY: the design system's DataTable injects the trailing row-actions column with
// `header: () => null`, so its `<th>` has no discernible text and axe flags every table
// that uses `rowActions`. There is no prop to name that column, so this cannot be fixed
// here — remove the exclusion once the design system names it. Every other rule, and
// every other element, is still scanned.
const DS_ROW_ACTIONS_COLUMN_HEADER_RULE = 'empty-table-header';

// TEMPORARY: the design system's row-actions overflow menu is a Radix `DropdownMenu`,
// which (like `Dialog`) hides the rest of the page from assistive tech while open —
// including the page's own `<h1>` and the landmark region it lives in. This app has no
// other a11y test that scans a `DropdownMenu` in its open state, so this is the first
// place it surfaces; it isn't something this page's markup does wrong (every other rule,
// and the menu's own contents, are still scanned). Remove once the design system either
// stops hiding the background for non-modal menus or names/lands them in a landmark.
const DS_DROPDOWN_MENU_HIDES_BACKGROUND_RULES = [
	'aria-hidden-focus',
	'landmark-one-main',
	'page-has-heading-one',
	'region',
];

const USERS = [createUser({username: 'jane.doe', name: 'Jane Doe', email: 'jane.doe@example.com'})];

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

test('should have no accessibility violations in the populated users page', async ({
	adminUsersPage,
	makeAxeBuilder,
}) => {
	await adminUsersPage.goto();
	await expect(adminUsersPage.cell('jane.doe')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_ROW_ACTIONS_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the create user dialog', async ({adminUsersPage, makeAxeBuilder}) => {
	await adminUsersPage.goto();
	await adminUsersPage.createUserButton.click();
	await expect(adminUsersPage.dialog).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the row actions menu', async ({
	adminUsersPage,
	makeAxeBuilder,
	page,
}) => {
	await adminUsersPage.goto();
	await adminUsersPage.rowActionsButton('jane.doe').click();
	await expect(page.getByRole('menuitem', {name: 'Edit user'})).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder()
		.disableRules([DS_ROW_ACTIONS_COLUMN_HEADER_RULE, ...DS_DROPDOWN_MENU_HIDES_BACKGROUND_RULES])
		.analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the delete user dialog', async ({adminUsersPage, makeAxeBuilder}) => {
	await adminUsersPage.goto();
	await adminUsersPage.deleteUser('jane.doe');
	await expect(adminUsersPage.alertDialog).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations on the user detail page', async ({
	adminUserDetailPage,
	makeAxeBuilder,
	network,
}) => {
	network.use(mockGetUserEndpoint({successResponse: HttpResponse.json(USERS[0]!)}));

	await adminUserDetailPage.goto('jane.doe');
	await expect(adminUserDetailPage.heading('jane.doe')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});
