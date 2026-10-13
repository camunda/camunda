/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {
	mockCurrentUserEndpoint,
	mockLicenseEndpoint,
	mockSystemConfigurationEndpoint,
	mockQueryTenantsEndpoint,
	mockGetTenantEndpoint,
	mockCreateTenantEndpoint,
	mockUpdateTenantEndpoint,
	mockDeleteTenantEndpoint,
	mockQueryUsersByTenantEndpoint,
	mockQueryClientsByTenantEndpoint,
	mockQueryGroupsByTenantEndpoint,
	mockQueryUsersEndpoint,
	mockAssignUserToTenantEndpoint,
	mockAssignClientToTenantEndpoint,
	mockUnassignUserFromTenantEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createQueryClientsByTenantResponse,
	createQueryGroupsByTenantResponse,
	createQueryTenantsResponse,
	createQueryUsersByTenantResponse,
	createTenant,
} from '#/shared-test-modules/api-mocks/tenants';

function mockSystem({isOidc}: {isOidc: boolean}) {
	return [
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(
				createSystemConfiguration({
					components: {active: ['admin']},
					authentication: {canLogout: true, isLoginDelegated: isOidc, isCamundaGroupsEnabled: true},
					deployment: {isMultiTenancyEnabled: false, isTenantsApiEnabled: true, maxRequestSize: 0},
				}),
			),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
	];
}

test.beforeEach(({network}) => {
	network.use(...mockSystem({isOidc: false}));
});

test.describe('Admin tenants list', () => {
	test('should list tenants', async ({adminTenantsPage, network}) => {
		network.use(
			mockQueryTenantsEndpoint({
				successResponse: HttpResponse.json(
					createQueryTenantsResponse([createTenant({tenantId: 'my-tenant', name: 'My tenant'})]),
				),
			}),
		);

		await adminTenantsPage.goto();

		await expect(adminTenantsPage.row('my-tenant')).toBeVisible();
	});

	test('should not offer row actions for the default tenant', async ({adminTenantsPage, network}) => {
		network.use(
			mockQueryTenantsEndpoint({
				successResponse: HttpResponse.json(
					createQueryTenantsResponse([createTenant({tenantId: '<default>', name: 'Default'})]),
				),
			}),
		);

		await adminTenantsPage.goto();

		await expect(adminTenantsPage.row('Default')).toBeVisible();
		await expect(adminTenantsPage.rowActionsButton('Default')).not.toBeVisible();
	});

	test('should create a tenant', async ({adminTenantsPage, network}) => {
		network.use(mockQueryTenantsEndpoint({successResponse: HttpResponse.json(createQueryTenantsResponse())}));

		await adminTenantsPage.goto();
		await adminTenantsPage.createButton.click();

		await expect(adminTenantsPage.createModal.dialog).toBeVisible();

		network.use(
			mockCreateTenantEndpoint({
				schema: z.object({
					tenantId: z.literal('my-tenant'),
					name: z.literal('My tenant'),
					description: z.literal('A tenant for tests'),
				}),
				successResponse: HttpResponse.json({}),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockQueryTenantsEndpoint({
				successResponse: HttpResponse.json(
					createQueryTenantsResponse([createTenant({tenantId: 'my-tenant', name: 'My tenant'})]),
				),
			}),
		);

		await adminTenantsPage.createModal.tenantIdInput.fill('my-tenant');
		await adminTenantsPage.createModal.nameInput.fill('My tenant');
		await adminTenantsPage.createModal.descriptionInput.fill('A tenant for tests');
		await adminTenantsPage.createModal.createButton.click();

		await expect(adminTenantsPage.createModal.dialog).not.toBeVisible();
		await expect(adminTenantsPage.row('my-tenant')).toBeVisible();
	});

	test('should edit a tenant with its fields prefilled', async ({adminTenantsPage, network}) => {
		network.use(
			mockQueryTenantsEndpoint({
				successResponse: HttpResponse.json(
					createQueryTenantsResponse([createTenant({tenantId: 'my-tenant', name: 'My tenant'})]),
				),
			}),
		);

		await adminTenantsPage.goto();
		await adminTenantsPage.rowActionsButton('my-tenant').click();
		await adminTenantsPage.menuItem('Edit').click();

		await expect(adminTenantsPage.editModal.dialog).toBeVisible();
		await expect(adminTenantsPage.editModal.nameInput).toHaveValue('My tenant');

		network.use(
			mockUpdateTenantEndpoint({
				schema: z.object({name: z.literal('Updated name')}),
				successResponse: HttpResponse.json({}),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockQueryTenantsEndpoint({
				successResponse: HttpResponse.json(
					createQueryTenantsResponse([createTenant({tenantId: 'my-tenant', name: 'Updated name'})]),
				),
			}),
		);

		await adminTenantsPage.editModal.nameInput.fill('Updated name');
		await adminTenantsPage.editModal.saveButton.click();

		await expect(adminTenantsPage.editModal.dialog).not.toBeVisible();
		await expect(adminTenantsPage.row('Updated name')).toBeVisible();
	});

	test('should delete a tenant', async ({adminTenantsPage, network}) => {
		network.use(
			mockQueryTenantsEndpoint({
				successResponse: HttpResponse.json(
					createQueryTenantsResponse([createTenant({tenantId: 'my-tenant', name: 'My tenant'})]),
				),
			}),
		);

		await adminTenantsPage.goto();
		await adminTenantsPage.rowActionsButton('my-tenant').click();
		await adminTenantsPage.menuItem('Delete').click();

		await expect(adminTenantsPage.deleteModal.dialog).toBeVisible();

		network.use(
			mockDeleteTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryTenantsEndpoint({successResponse: HttpResponse.json(createQueryTenantsResponse())}),
		);

		await adminTenantsPage.deleteModal.confirmButton.click();

		await expect(adminTenantsPage.deleteModal.dialog).not.toBeVisible();
		await expect(adminTenantsPage.row('my-tenant')).not.toBeVisible();
	});

	test('should search tenants by exact ID', async ({adminTenantsPage, network}) => {
		network.use(mockQueryTenantsEndpoint({successResponse: HttpResponse.json(createQueryTenantsResponse())}));

		await adminTenantsPage.goto();
		await expect(adminTenantsPage.searchInput).toBeVisible();

		network.use(
			mockQueryTenantsEndpoint({
				schema: z.object({filter: z.object({tenantId: z.literal('my-tenant')})}),
				successResponse: HttpResponse.json(
					createQueryTenantsResponse([createTenant({tenantId: 'my-tenant', name: 'My tenant'})]),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await adminTenantsPage.searchInput.fill('my-tenant');

		await expect(adminTenantsPage.row('my-tenant')).toBeVisible();
	});

	test('should open the tenant detail page when a row is clicked', async ({
		adminTenantsPage,
		adminTenantDetailPage,
		network,
	}) => {
		const tenant = createTenant({tenantId: 'my-tenant', name: 'My tenant'});
		network.use(
			mockQueryTenantsEndpoint({successResponse: HttpResponse.json(createQueryTenantsResponse([tenant]))}),
			mockGetTenantEndpoint({successResponse: HttpResponse.json(tenant)}),
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
		);

		await adminTenantsPage.goto();
		await adminTenantsPage.row('my-tenant').click();

		await expect(adminTenantDetailPage.heading).toHaveText('My tenant');
	});
});

test.describe('Admin tenant detail', () => {
	const tenant = createTenant({tenantId: 'my-tenant', name: 'My tenant', description: 'A tenant for tests'});

	test.beforeEach(({network}) => {
		network.use(mockGetTenantEndpoint({successResponse: HttpResponse.json(tenant)}));
	});

	test('should show the users of the tenant', async ({adminTenantDetailPage, network}) => {
		network.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['jane']))}),
		);

		await adminTenantDetailPage.goto('my-tenant');

		await expect(adminTenantDetailPage.row('jane')).toBeVisible();
	});

	test('should not offer mapping rule and client tabs without OIDC', async ({adminTenantDetailPage, network}) => {
		network.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
		);

		await adminTenantDetailPage.goto('my-tenant');

		await expect(adminTenantDetailPage.tab('Groups')).toBeVisible();
		await expect(adminTenantDetailPage.tab('Mapping rules')).not.toBeVisible();
		await expect(adminTenantDetailPage.tab('Clients')).not.toBeVisible();
	});

	test('should show the groups of the tenant after switching tab', async ({adminTenantDetailPage, page, network}) => {
		network.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
			mockQueryGroupsByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryGroupsByTenantResponse(['sales'])),
			}),
		);

		await adminTenantDetailPage.goto('my-tenant');
		await adminTenantDetailPage.tab('Groups').click();

		await expect(page).toHaveURL(/tab=groups/);
		await expect(adminTenantDetailPage.row('sales')).toBeVisible();
	});

	test('should assign a user picked from the user search', async ({adminTenantDetailPage, page, network}) => {
		network.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json({
					items: [{username: 'jane', name: 'Jane', email: 'jane@example.com'}],
					page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
				}),
			}),
		);

		await adminTenantDetailPage.goto('my-tenant');
		await adminTenantDetailPage.assignButton('Assign user').click();

		await expect(adminTenantDetailPage.assignUserModal.dialog).toBeVisible();

		network.use(
			mockAssignUserToTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['jane']))}),
		);

		await adminTenantDetailPage.assignUserModal.combobox.click();
		await page.getByRole('option', {name: 'jane'}).click();
		await adminTenantDetailPage.assignUserModal.assignButton.click();

		await expect(adminTenantDetailPage.assignUserModal.dialog).not.toBeVisible();
		await expect(adminTenantDetailPage.row('jane')).toBeVisible();
	});

	test('should unassign a user after confirmation', async ({adminTenantDetailPage, network}) => {
		network.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['jane']))}),
		);

		await adminTenantDetailPage.goto('my-tenant');
		await adminTenantDetailPage.rowActionsButton('jane').click();
		await adminTenantDetailPage.removeMenuItem.click();

		await expect(adminTenantDetailPage.unassignModal.dialog).toBeVisible();

		network.use(
			mockUnassignUserFromTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
		);

		await adminTenantDetailPage.unassignModal.confirmButton.click();

		await expect(adminTenantDetailPage.unassignModal.dialog).not.toBeVisible();
		await expect(adminTenantDetailPage.row('jane')).not.toBeVisible();
	});

	test('should delete the tenant and return to the list', async ({
		adminTenantDetailPage,
		adminTenantsPage,
		network,
	}) => {
		network.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
		);

		await adminTenantDetailPage.goto('my-tenant');
		await adminTenantDetailPage.deleteButton.click();

		network.use(
			mockDeleteTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryTenantsEndpoint({successResponse: HttpResponse.json(createQueryTenantsResponse())}),
		);

		await adminTenantsPage.deleteModal.confirmButton.click();

		await expect(adminTenantsPage.createButton).toBeVisible();
	});

	test('should show the OIDC-only tabs and assign a client by ID', async ({adminTenantDetailPage, network}) => {
		network.use(
			...mockSystem({isOidc: true}),
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
			mockQueryClientsByTenantEndpoint({successResponse: HttpResponse.json(createQueryClientsByTenantResponse())}),
		);

		await adminTenantDetailPage.goto('my-tenant', 'clients');
		await expect(adminTenantDetailPage.tab('Mapping rules')).toBeVisible();
		await adminTenantDetailPage.assignButton('Assign client').click();

		network.use(
			mockAssignClientToTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryClientsByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryClientsByTenantResponse(['connectors'])),
			}),
		);

		await adminTenantDetailPage.assignClientModal.textInput.fill('connectors');
		await adminTenantDetailPage.assignClientModal.assignButton.click();

		await expect(adminTenantDetailPage.row('connectors')).toBeVisible();
	});
});
