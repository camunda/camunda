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
	mockQueryMappingRulesEndpoint,
	mockCreateMappingRuleEndpoint,
	mockUpdateMappingRuleEndpoint,
	mockDeleteMappingRuleEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createMappingRule, createQueryMappingRulesResponse} from '#/shared-test-modules/api-mocks/mapping-rules';

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
	);
});

test.describe('Admin mapping rules', () => {
	test('should list mapping rules', async ({adminMappingRulesPage, network}) => {
		network.use(
			mockQueryMappingRulesEndpoint({
				successResponse: HttpResponse.json(
					createQueryMappingRulesResponse({
						items: [createMappingRule({mappingRuleId: 'my-rule', name: 'My rule', claimName: 'email'})],
					}),
				),
			}),
		);

		await adminMappingRulesPage.goto();

		await expect(adminMappingRulesPage.row('my-rule')).toBeVisible();
	});

	test('should create a mapping rule', async ({adminMappingRulesPage, network}) => {
		network.use(mockQueryMappingRulesEndpoint({successResponse: HttpResponse.json(createQueryMappingRulesResponse())}));

		await adminMappingRulesPage.goto();
		await adminMappingRulesPage.addButton.click();

		await expect(adminMappingRulesPage.addModal.dialog).toBeVisible();

		network.use(
			mockCreateMappingRuleEndpoint({
				schema: z.object({
					mappingRuleId: z.literal('my-rule'),
					name: z.literal('My rule'),
					claimName: z.literal('email'),
					claimValue: z.literal('demo@example.com'),
				}),
				successResponse: HttpResponse.json({}),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockQueryMappingRulesEndpoint({
				successResponse: HttpResponse.json(
					createQueryMappingRulesResponse({
						items: [createMappingRule({mappingRuleId: 'my-rule', name: 'My rule', claimName: 'email'})],
					}),
				),
			}),
		);

		await adminMappingRulesPage.addModal.mappingRuleIdInput.fill('my-rule');
		await adminMappingRulesPage.addModal.nameInput.fill('My rule');
		await adminMappingRulesPage.addModal.claimNameInput.fill('email');
		await adminMappingRulesPage.addModal.claimValueInput.fill('demo@example.com');
		await adminMappingRulesPage.addModal.saveButton.click();

		await expect(adminMappingRulesPage.addModal.dialog).not.toBeVisible();
		await expect(adminMappingRulesPage.row('my-rule')).toBeVisible();
	});

	test('should edit a mapping rule with its fields prefilled', async ({adminMappingRulesPage, network}) => {
		network.use(
			mockQueryMappingRulesEndpoint({
				successResponse: HttpResponse.json(
					createQueryMappingRulesResponse({
						items: [createMappingRule({mappingRuleId: 'my-rule', name: 'My rule', claimName: 'email'})],
					}),
				),
			}),
		);

		await adminMappingRulesPage.goto();
		await adminMappingRulesPage.rowActionsButton('my-rule').click();
		await adminMappingRulesPage.menuItem('Edit').click();

		await expect(adminMappingRulesPage.editModal.dialog).toBeVisible();
		await expect(adminMappingRulesPage.editModal.nameInput).toHaveValue('My rule');

		network.use(
			mockUpdateMappingRuleEndpoint({
				schema: z.object({name: z.literal('Updated name'), claimName: z.string(), claimValue: z.string()}),
				successResponse: HttpResponse.json({}),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockQueryMappingRulesEndpoint({
				successResponse: HttpResponse.json(
					createQueryMappingRulesResponse({
						items: [createMappingRule({mappingRuleId: 'my-rule', name: 'Updated name', claimName: 'email'})],
					}),
				),
			}),
		);

		await adminMappingRulesPage.editModal.nameInput.fill('Updated name');
		await adminMappingRulesPage.editModal.saveButton.click();

		await expect(adminMappingRulesPage.editModal.dialog).not.toBeVisible();
		await expect(adminMappingRulesPage.row('Updated name')).toBeVisible();
	});

	test('should delete a mapping rule', async ({adminMappingRulesPage, network}) => {
		network.use(
			mockQueryMappingRulesEndpoint({
				successResponse: HttpResponse.json(
					createQueryMappingRulesResponse({
						items: [createMappingRule({mappingRuleId: 'my-rule', name: 'My rule'})],
					}),
				),
			}),
		);

		await adminMappingRulesPage.goto();
		await adminMappingRulesPage.rowActionsButton('my-rule').click();
		await adminMappingRulesPage.menuItem('Delete').click();

		await expect(adminMappingRulesPage.deleteModal.dialog).toBeVisible();

		network.use(
			mockDeleteMappingRuleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryMappingRulesEndpoint({successResponse: HttpResponse.json(createQueryMappingRulesResponse())}),
		);

		await adminMappingRulesPage.deleteModal.confirmButton.click();

		await expect(adminMappingRulesPage.deleteModal.dialog).not.toBeVisible();
		await expect(adminMappingRulesPage.row('my-rule')).not.toBeVisible();
	});

	test('should search mapping rules by ID', async ({adminMappingRulesPage, network}) => {
		network.use(mockQueryMappingRulesEndpoint({successResponse: HttpResponse.json(createQueryMappingRulesResponse())}));

		await adminMappingRulesPage.goto();
		await expect(adminMappingRulesPage.searchInput).toBeVisible();

		network.use(
			mockQueryMappingRulesEndpoint({
				schema: z.object({filter: z.object({mappingRuleId: z.object({$like: z.literal('*my-rule*')})})}),
				successResponse: HttpResponse.json(
					createQueryMappingRulesResponse({
						items: [createMappingRule({mappingRuleId: 'my-rule', name: 'My rule'})],
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await adminMappingRulesPage.searchInput.fill('my-rule');

		await expect(adminMappingRulesPage.row('my-rule')).toBeVisible();
	});
});
