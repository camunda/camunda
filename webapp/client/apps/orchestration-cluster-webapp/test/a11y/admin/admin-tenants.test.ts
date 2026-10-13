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
	mockQueryTenantsEndpoint,
	mockGetTenantEndpoint,
	mockQueryUsersByTenantEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createQueryTenantsResponse,
	createQueryUsersByTenantResponse,
	createTenant,
} from '#/shared-test-modules/api-mocks/tenants';

// TEMPORARY: the design system's DataTable injects the row-actions column with
// `header: () => null`, so its `<th>` has no discernible text and axe flags every table
// that uses `rowActions`. There is no prop to name that column, so this cannot be fixed
// here — remove the exclusion once the design system names it. Every other rule, and
// every other element, is still scanned.
const DS_ACTIONS_COLUMN_HEADER_RULE = 'empty-table-header';

const tenant = createTenant({tenantId: 'sales', name: 'Sales', description: 'The sales department'});

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(
				createSystemConfiguration({
					components: {active: ['admin']},
					authentication: {canLogout: true, isLoginDelegated: false, isCamundaGroupsEnabled: true},
					deployment: {isMultiTenancyEnabled: false, isTenantsApiEnabled: true, maxRequestSize: 0},
				}),
			),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryTenantsEndpoint({successResponse: HttpResponse.json(createQueryTenantsResponse([tenant]))}),
		mockGetTenantEndpoint({successResponse: HttpResponse.json(tenant)}),
		mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['jane']))}),
	);
});

test('should have no accessibility violations in the populated tenants page', async ({
	adminTenantsPage,
	makeAxeBuilder,
}) => {
	await adminTenantsPage.goto();
	await expect(adminTenantsPage.row('sales')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_ACTIONS_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the empty state', async ({
	adminTenantsPage,
	makeAxeBuilder,
	network,
	page,
}) => {
	network.use(mockQueryTenantsEndpoint({successResponse: HttpResponse.json(createQueryTenantsResponse())}));

	await adminTenantsPage.goto();
	await expect(page.getByText('No tenants found.')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_ACTIONS_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations with the create tenant modal open', async ({
	adminTenantsPage,
	makeAxeBuilder,
}) => {
	await adminTenantsPage.goto();
	await adminTenantsPage.createButton.click();
	await expect(adminTenantsPage.createModal.dialog).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the tenant detail page', async ({
	adminTenantDetailPage,
	makeAxeBuilder,
}) => {
	await adminTenantDetailPage.goto('sales');
	await expect(adminTenantDetailPage.row('jane')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_ACTIONS_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations with the assign user modal open', async ({
	adminTenantDetailPage,
	makeAxeBuilder,
}) => {
	await adminTenantDetailPage.goto('sales');
	await adminTenantDetailPage.assignButton('Assign user').click();
	await expect(adminTenantDetailPage.assignUserModal.dialog).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});
