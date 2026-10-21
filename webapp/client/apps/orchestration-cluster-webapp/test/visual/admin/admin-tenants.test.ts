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
		mockQueryUsersByTenantEndpoint({
			successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['jane', 'john'])),
		}),
	);
});

test('should match the tenants page snapshot', async ({adminTenantsPage, page}) => {
	await adminTenantsPage.goto();
	await expect(adminTenantsPage.row('sales')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the create tenant modal snapshot', async ({adminTenantsPage, page}) => {
	await adminTenantsPage.goto();
	await adminTenantsPage.createButton.click();

	await expect(adminTenantsPage.createModal.dialog).toBeVisible();
	await expect(page).toHaveScreenshot();
});

test('should match the tenant detail page snapshot', async ({adminTenantDetailPage, page}) => {
	await adminTenantDetailPage.goto('sales');
	await expect(adminTenantDetailPage.row('jane')).toBeVisible();

	await expect(page).toHaveScreenshot();
});
