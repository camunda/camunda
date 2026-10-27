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
	mockQueryClusterVariablesEndpoint,
	mockGetGlobalClusterVariableEndpoint,
	mockCreateGlobalClusterVariableEndpoint,
	mockUpdateGlobalClusterVariableEndpoint,
	mockDeleteGlobalClusterVariableEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createClusterVariable,
	createClusterVariableSearchResult,
	createQueryClusterVariablesResponse,
} from '#/shared-test-modules/api-mocks/cluster-variables';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['admin']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
	);
});

test.describe('Admin cluster variables', () => {
	test('should list cluster variables', async ({adminClusterVariablesPage, network}) => {
		network.use(
			mockQueryClusterVariablesEndpoint({
				successResponse: HttpResponse.json(
					createQueryClusterVariablesResponse([
						createClusterVariableSearchResult({name: 'my-variable', value: '"my value"'}),
						createClusterVariableSearchResult({name: 'tenant-variable', scope: 'TENANT', tenantId: 'tenant-a'}),
					]),
				),
			}),
		);

		await adminClusterVariablesPage.goto();

		await expect(adminClusterVariablesPage.row('my-variable')).toContainText('Global');
		await expect(adminClusterVariablesPage.row('tenant-variable')).toContainText('Tenant: tenant-a');
	});

	test('should create a cluster variable', async ({adminClusterVariablesPage, network}) => {
		network.use(
			mockQueryClusterVariablesEndpoint({successResponse: HttpResponse.json(createQueryClusterVariablesResponse())}),
		);

		await adminClusterVariablesPage.goto();
		await adminClusterVariablesPage.addButton.click();

		await expect(adminClusterVariablesPage.addModal.dialog).toBeVisible();

		network.use(
			mockCreateGlobalClusterVariableEndpoint({
				schema: z.object({name: z.literal('my-variable'), value: z.literal(42)}),
				successResponse: HttpResponse.json(createClusterVariable({name: 'my-variable', value: '42'})),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockGetGlobalClusterVariableEndpoint({
				successResponse: HttpResponse.json(createClusterVariable({name: 'my-variable', value: '42'})),
			}),
			mockQueryClusterVariablesEndpoint({
				successResponse: HttpResponse.json(
					createQueryClusterVariablesResponse([createClusterVariableSearchResult({name: 'my-variable', value: '42'})]),
				),
			}),
		);

		await adminClusterVariablesPage.addModal.nameInput.fill('my-variable');
		await adminClusterVariablesPage.addModal.fillValue('42');
		await adminClusterVariablesPage.addModal.createButton.click();

		await expect(adminClusterVariablesPage.addModal.dialog).not.toBeVisible();
		await expect(adminClusterVariablesPage.row('my-variable')).toBeVisible();
	});

	test('should disable the tenant scope when the tenants API is disabled', async ({
		adminClusterVariablesPage,
		network,
	}) => {
		network.use(
			mockQueryClusterVariablesEndpoint({successResponse: HttpResponse.json(createQueryClusterVariablesResponse())}),
		);

		await adminClusterVariablesPage.goto();
		await adminClusterVariablesPage.addButton.click();

		await expect(adminClusterVariablesPage.addModal.tenantScopeRadio).toBeDisabled();
	});

	test('should enable the tenant scope when the tenants API is enabled', async ({
		adminClusterVariablesPage,
		network,
	}) => {
		network.use(
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(
					createSystemConfiguration({
						components: {active: ['admin']},
						deployment: {isMultiTenancyEnabled: true, isTenantsApiEnabled: true, maxRequestSize: 0},
					}),
				),
			}),
			mockQueryClusterVariablesEndpoint({successResponse: HttpResponse.json(createQueryClusterVariablesResponse())}),
		);

		await adminClusterVariablesPage.goto();
		await adminClusterVariablesPage.addButton.click();

		await expect(adminClusterVariablesPage.addModal.tenantScopeRadio).toBeEnabled();
	});

	test('should view the full value of a truncated cluster variable', async ({adminClusterVariablesPage, network}) => {
		network.use(
			mockQueryClusterVariablesEndpoint({
				successResponse: HttpResponse.json(
					createQueryClusterVariablesResponse([
						createClusterVariableSearchResult({name: 'my-variable', value: '"the fu', isTruncated: true}),
					]),
				),
			}),
			mockGetGlobalClusterVariableEndpoint({
				successResponse: HttpResponse.json(createClusterVariable({name: 'my-variable', value: '"the full value"'})),
			}),
		);

		await adminClusterVariablesPage.goto();
		await adminClusterVariablesPage.rowActionsButton('my-variable').click();
		await adminClusterVariablesPage.menuItem('View').click();

		await expect(adminClusterVariablesPage.viewModal.dialog).toBeVisible();
		await expect(adminClusterVariablesPage.viewModal.dialog).toContainText('"the full value"');
	});

	test('should edit the value of a cluster variable', async ({adminClusterVariablesPage, network}) => {
		network.use(
			mockQueryClusterVariablesEndpoint({
				successResponse: HttpResponse.json(
					createQueryClusterVariablesResponse([createClusterVariableSearchResult({name: 'my-variable', value: '1'})]),
				),
			}),
			mockGetGlobalClusterVariableEndpoint({
				successResponse: HttpResponse.json(createClusterVariable({name: 'my-variable', value: '1'})),
			}),
		);

		await adminClusterVariablesPage.goto();
		await adminClusterVariablesPage.rowActionsButton('my-variable').click();
		await adminClusterVariablesPage.menuItem('Edit').click();

		await expect(adminClusterVariablesPage.editModal.dialog).toBeVisible();
		await expect(adminClusterVariablesPage.editModal.nameInput).toHaveValue('my-variable');
		await expect(adminClusterVariablesPage.editModal.nameInput).toBeDisabled();
		await expect(adminClusterVariablesPage.editModal.saveButton).toBeDisabled();

		network.use(
			mockUpdateGlobalClusterVariableEndpoint({
				schema: z.object({value: z.literal(2)}),
				successResponse: HttpResponse.json(createClusterVariable({name: 'my-variable', value: '2'})),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockGetGlobalClusterVariableEndpoint({
				successResponse: HttpResponse.json(createClusterVariable({name: 'my-variable', value: '2'})),
			}),
			mockQueryClusterVariablesEndpoint({
				successResponse: HttpResponse.json(
					createQueryClusterVariablesResponse([createClusterVariableSearchResult({name: 'my-variable', value: '2'})]),
				),
			}),
		);

		await adminClusterVariablesPage.editModal.fillValue('2');
		await adminClusterVariablesPage.editModal.saveButton.click();

		await expect(adminClusterVariablesPage.editModal.dialog).not.toBeVisible();
		await expect(adminClusterVariablesPage.row('my-variable')).toContainText('2');
	});

	test('should delete a cluster variable', async ({adminClusterVariablesPage, network}) => {
		network.use(
			mockQueryClusterVariablesEndpoint({
				successResponse: HttpResponse.json(
					createQueryClusterVariablesResponse([createClusterVariableSearchResult({name: 'my-variable'})]),
				),
			}),
		);

		await adminClusterVariablesPage.goto();
		await adminClusterVariablesPage.rowActionsButton('my-variable').click();
		await adminClusterVariablesPage.menuItem('Delete').click();

		await expect(adminClusterVariablesPage.deleteModal.dialog).toBeVisible();

		network.use(
			mockDeleteGlobalClusterVariableEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetGlobalClusterVariableEndpoint({successResponse: new HttpResponse(null, {status: 404})}),
			mockQueryClusterVariablesEndpoint({successResponse: HttpResponse.json(createQueryClusterVariablesResponse())}),
		);

		await adminClusterVariablesPage.deleteModal.confirmButton.click();

		await expect(adminClusterVariablesPage.deleteModal.dialog).not.toBeVisible();
		await expect(adminClusterVariablesPage.row('my-variable')).not.toBeVisible();
	});

	test('should search cluster variables by name', async ({adminClusterVariablesPage, network}) => {
		network.use(
			mockQueryClusterVariablesEndpoint({successResponse: HttpResponse.json(createQueryClusterVariablesResponse())}),
		);

		await adminClusterVariablesPage.goto();
		await expect(adminClusterVariablesPage.searchInput).toBeVisible();

		network.use(
			mockQueryClusterVariablesEndpoint({
				schema: z.object({filter: z.object({name: z.object({$like: z.literal('*my-var*')})})}),
				successResponse: HttpResponse.json(
					createQueryClusterVariablesResponse([createClusterVariableSearchResult({name: 'my-variable'})]),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await adminClusterVariablesPage.searchInput.fill('my-var');

		await expect(adminClusterVariablesPage.row('my-variable')).toBeVisible();
	});
});
