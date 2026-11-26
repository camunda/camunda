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
	mockCreateMappingRuleEndpoint,
	mockCurrentUserEndpoint,
	mockDeleteMappingRuleEndpoint,
	mockGetMappingRuleEndpoint,
	mockLicenseEndpoint,
	mockQueryMappingRulesEndpoint,
	mockSystemConfigurationEndpoint,
	mockUpdateMappingRuleEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createMappingRule, createQueryMappingRulesResponse} from '#/shared-test-modules/api-mocks/mapping-rules';

const OKTA = createMappingRule({
	mappingRuleId: 'okta-rule',
	name: 'Okta users',
	claimName: 'groups',
	claimValue: 'okta',
});
const AZURE = createMappingRule({
	mappingRuleId: 'azure-rule',
	name: 'Azure users',
	claimName: 'groups',
	claimValue: 'azure',
});

test.beforeEach(async ({network, adminMappingRulesPage}) => {
	await adminMappingRulesPage.mockClientConfig();
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
		mockQueryMappingRulesEndpoint({
			successResponse: HttpResponse.json(createQueryMappingRulesResponse({items: [OKTA, AZURE]})),
		}),
	);
});

test.describe('Admin mapping rules before the search index confirms a change', () => {
	test('should show the edited values in the list while the search index still has the old ones', async ({
		adminMappingRulesPage,
		network,
	}) => {
		await adminMappingRulesPage.goto();
		await expect(adminMappingRulesPage.row('Okta users')).toBeVisible();

		await adminMappingRulesPage.rowActionsButton('okta-rule').click();
		await adminMappingRulesPage.menuItem('Edit').click();
		await adminMappingRulesPage.editModal.nameInput.fill('Okta admins');

		const edited = {...OKTA, name: 'Okta admins'};
		network.use(
			mockUpdateMappingRuleEndpoint({
				schema: z.object({
					name: z.literal('Okta admins'),
					claimName: z.literal('groups'),
					claimValue: z.literal('okta'),
				}),
				successResponse: HttpResponse.json(edited),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockGetMappingRuleEndpoint({successResponse: HttpResponse.json(edited)}),
			mockQueryMappingRulesEndpoint({
				successResponse: HttpResponse.json(createQueryMappingRulesResponse({items: [OKTA, AZURE]})),
				delay: 'infinite',
			}),
		);
		await adminMappingRulesPage.editModal.saveButton.click();

		await expect(adminMappingRulesPage.editModal.dialog).toBeHidden();
		await expect(adminMappingRulesPage.row('Okta admins')).toBeVisible();
		await expect(adminMappingRulesPage.row('Okta users')).toBeHidden();
		await expect(adminMappingRulesPage.row('Azure users')).toBeVisible();
	});

	test('should drop a deleted mapping rule from the list while the search index still has it', async ({
		adminMappingRulesPage,
		network,
	}) => {
		await adminMappingRulesPage.goto();
		await expect(adminMappingRulesPage.row('okta-rule')).toBeVisible();

		await adminMappingRulesPage.rowActionsButton('okta-rule').click();
		await adminMappingRulesPage.menuItem('Delete').click();
		await expect(adminMappingRulesPage.deleteModal.dialog).toBeVisible();

		network.use(
			mockDeleteMappingRuleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetMappingRuleEndpoint({successResponse: new HttpResponse(null, {status: 404})}),
			mockQueryMappingRulesEndpoint({
				successResponse: HttpResponse.json(createQueryMappingRulesResponse({items: [OKTA, AZURE]})),
				delay: 'infinite',
			}),
		);
		await adminMappingRulesPage.deleteModal.confirmButton.click();

		await expect(adminMappingRulesPage.deleteModal.dialog).toBeHidden();
		await expect(adminMappingRulesPage.row('azure-rule')).toBeVisible();
		await expect(adminMappingRulesPage.row('okta-rule')).toBeHidden();
	});

	test('should not add a created mapping rule to a list filtered by another ID', async ({
		adminMappingRulesPage,
		network,
	}) => {
		await adminMappingRulesPage.goto();
		await expect(adminMappingRulesPage.row('okta-rule')).toBeVisible();

		network.use(
			mockQueryMappingRulesEndpoint({
				schema: z.object({filter: z.object({mappingRuleId: z.object({$like: z.literal('*azure*')})})}),
				successResponse: HttpResponse.json(createQueryMappingRulesResponse({items: [AZURE]})),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);
		await adminMappingRulesPage.searchInput.fill('azure');
		await expect(adminMappingRulesPage.row('okta-rule')).toBeHidden();
		await expect(adminMappingRulesPage.row('azure-rule')).toBeVisible();

		await adminMappingRulesPage.addButton.click();
		await adminMappingRulesPage.addModal.mappingRuleIdInput.fill('google-rule');
		await adminMappingRulesPage.addModal.nameInput.fill('Google users');
		await adminMappingRulesPage.addModal.claimNameInput.fill('groups');
		await adminMappingRulesPage.addModal.claimValueInput.fill('google');

		const created = createMappingRule({
			mappingRuleId: 'google-rule',
			name: 'Google users',
			claimName: 'groups',
			claimValue: 'google',
		});
		network.use(
			mockCreateMappingRuleEndpoint({
				schema: z.object({
					mappingRuleId: z.literal('google-rule'),
					name: z.literal('Google users'),
					claimName: z.literal('groups'),
					claimValue: z.literal('google'),
				}),
				successResponse: HttpResponse.json(created),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockGetMappingRuleEndpoint({successResponse: HttpResponse.json(created)}),
			mockQueryMappingRulesEndpoint({
				successResponse: HttpResponse.json(createQueryMappingRulesResponse({items: [AZURE]})),
				delay: 'infinite',
			}),
		);
		await adminMappingRulesPage.addModal.saveButton.click();

		await expect(adminMappingRulesPage.addModal.dialog).toBeHidden();
		await expect(adminMappingRulesPage.row('azure-rule')).toBeVisible();
		await expect(adminMappingRulesPage.row('google-rule')).toBeHidden();
	});
});
