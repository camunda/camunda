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
	mockQueryMappingRulesEndpoint,
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
		mockQueryMappingRulesEndpoint({
			successResponse: HttpResponse.json(
				createQueryMappingRulesResponse({
					items: [
						createMappingRule({
							mappingRuleId: 'sales-team',
							name: 'Sales team',
							claimName: 'department',
							claimValue: 'sales',
						}),
					],
				}),
			),
		}),
	);
});

test('should match the mapping rules page snapshot', async ({adminMappingRulesPage, page}) => {
	await adminMappingRulesPage.goto();
	await expect(adminMappingRulesPage.row('sales-team')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the add mapping rule modal snapshot', async ({adminMappingRulesPage, page}) => {
	await adminMappingRulesPage.goto();
	await adminMappingRulesPage.addButton.click();

	await expect(adminMappingRulesPage.addModal.dialog).toBeVisible();
	await expect(page).toHaveScreenshot();
});
