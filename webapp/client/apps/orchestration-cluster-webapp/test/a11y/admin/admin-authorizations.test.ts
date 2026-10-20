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
	mockSystemConfigurationEndpoint,
	mockQueryAuthorizationsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createAuthorization, createQueryAuthorizationsResponse} from '#/shared-test-modules/api-mocks/authorizations';

// TEMPORARY: the design system's DataTable injects the row-actions column with
// `header: () => null`, so its `<th>` has no discernible text and axe flags every table
// that uses `rowActions`. There is no prop to name that column, so this cannot be fixed
// here — remove the exclusion once the design system names it. Every other rule, and
// every other element, is still scanned.
const DS_ACTIONS_COLUMN_HEADER_RULE = 'empty-table-header';

test.beforeEach(async ({network, adminAuthorizationsPage}) => {
	await adminAuthorizationsPage.mockClientConfig();
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
		mockQueryAuthorizationsEndpoint({
			successResponse: HttpResponse.json(
				createQueryAuthorizationsResponse({items: [createAuthorization({ownerId: 'john.doe'})]}),
			),
		}),
	);
});

test('should have no accessibility violations in the populated authorizations page', async ({
	adminAuthorizationsPage,
	makeAxeBuilder,
}) => {
	await adminAuthorizationsPage.goto('?resourceType=PROCESS_DEFINITION');
	await expect(adminAuthorizationsPage.row('john.doe')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_ACTIONS_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the empty state', async ({
	adminAuthorizationsPage,
	makeAxeBuilder,
	network,
}) => {
	network.use(
		mockQueryAuthorizationsEndpoint({successResponse: HttpResponse.json(createQueryAuthorizationsResponse())}),
	);

	await adminAuthorizationsPage.goto('?resourceType=PROCESS_DEFINITION');
	await expect(adminAuthorizationsPage.emptyState).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_ACTIONS_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations with the create authorization modal open', async ({
	adminAuthorizationsPage,
	makeAxeBuilder,
}) => {
	await adminAuthorizationsPage.goto('?resourceType=PROCESS_DEFINITION');
	await adminAuthorizationsPage.addButton.click();
	await expect(adminAuthorizationsPage.addModal.dialog).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_ACTIONS_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});
