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
	mockQueryMappingRulesEndpoint,
	mockQueryUsersEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryMappingRulesResponse} from '#/shared-test-modules/api-mocks/mapping-rules';
import {createQueryUsersResponse} from '#/shared-test-modules/api-mocks/users';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser()),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['admin']}})),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
		mockQueryUsersEndpoint({successResponse: HttpResponse.json(createQueryUsersResponse())}),
	);
});

test.describe('Admin index page', () => {
	test('should redirect to users', async ({adminIndexPage, page}) => {
		await adminIndexPage.goto();

		await expect(page).toHaveURL('/admin/users');
		await expect(adminIndexPage.sectionHeading('Users')).toBeVisible();
		await expect(adminIndexPage.branding).toBeVisible();
	});

	test('should redirect to mapping rules when login is delegated', async ({adminIndexPage, network, page}) => {
		network.use(
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(
					createSystemConfiguration({
						components: {active: ['admin']},
						authentication: {canLogout: true, isLoginDelegated: true, isCamundaGroupsEnabled: true},
					}),
				),
			}),
			mockQueryMappingRulesEndpoint({successResponse: HttpResponse.json(createQueryMappingRulesResponse())}),
		);

		await adminIndexPage.goto();

		await expect(page).toHaveURL('/admin/mapping-rules');
	});
});
