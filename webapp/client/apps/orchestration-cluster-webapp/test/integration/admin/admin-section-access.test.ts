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
	mockQueryGroupsEndpoint,
	mockQueryMappingRulesEndpoint,
	mockQueryTenantsEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createAdminClientConfigScript} from '#/shared-test-modules/api-mocks/authorizations';
import {SAAS_CLUSTER_ID, SAAS_ORGANIZATION_ID} from '#/shared-test-modules/api-mocks/saas';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryGroupsResponse} from '#/shared-test-modules/api-mocks/groups';
import {createQueryMappingRulesResponse} from '#/shared-test-modules/api-mocks/mapping-rules';
import {createQueryTenantsResponse} from '#/shared-test-modules/api-mocks/tenants';
import {createSaasAdminHandlers} from './saasAdminHandlers';

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
	);
});

test.describe('Admin section access', () => {
	test('should not serve tenants until the tenants API is enabled', async ({adminIndexPage, notFoundPage, network}) => {
		await adminIndexPage.gotoSection('tenants');

		await expect(notFoundPage.heading).toBeVisible();

		network.use(
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(
					createSystemConfiguration({
						components: {active: ['admin']},
						deployment: {
							isMultiTenancyEnabled: false,
							isTenantsApiEnabled: true,
							isWaitStatesEnabled: true,
							maxRequestSize: 0,
						},
					}),
				),
			}),
			mockQueryTenantsEndpoint({successResponse: HttpResponse.json(createQueryTenantsResponse())}),
		);
		await adminIndexPage.gotoSection('tenants');

		await expect(adminIndexPage.sectionHeading('Tenants')).toBeVisible();
	});

	test('should not serve groups once the identity provider owns group membership', async ({
		adminIndexPage,
		adminGroupsPage,
		notFoundPage,
		network,
	}) => {
		await adminGroupsPage.mockClientConfig();
		network.use(mockQueryGroupsEndpoint({successResponse: HttpResponse.json(createQueryGroupsResponse())}));

		await adminIndexPage.gotoSection('groups');

		await expect(adminIndexPage.sectionHeading('Groups')).toBeVisible();

		network.use(
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(
					createSystemConfiguration({
						components: {active: ['admin']},
						authentication: {canLogout: true, isLoginDelegated: true, isCamundaGroupsEnabled: false},
					}),
				),
			}),
		);
		await adminIndexPage.gotoSection('groups');

		await expect(notFoundPage.heading).toBeVisible();
	});

	test('should not serve mapping rules while Camunda owns the users', async ({adminIndexPage, notFoundPage}) => {
		await adminIndexPage.gotoSection('mapping-rules');

		await expect(notFoundPage.heading).toBeVisible();
	});

	test('should serve mapping rules instead of users when login is delegated', async ({
		adminIndexPage,
		adminMappingRulesPage,
		notFoundPage,
		network,
	}) => {
		await adminMappingRulesPage.mockClientConfig();
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

		await adminIndexPage.gotoSection('mapping-rules');

		await expect(adminIndexPage.sectionHeading('Mapping rules')).toBeVisible();

		await adminIndexPage.gotoSection('users');

		await expect(notFoundPage.heading).toBeVisible();
	});

	test('should serve mapping rules on SaaS only with an additional IdP', async ({
		adminIndexPage,
		adminMappingRulesPage,
		notFoundPage,
		network,
	}) => {
		await adminIndexPage.seedSaasBootConfig({organizationId: SAAS_ORGANIZATION_ID, clusterId: SAAS_CLUSTER_ID});
		await adminMappingRulesPage.mockClientConfig();
		network.use(...createSaasAdminHandlers());

		await adminIndexPage.gotoSection('mapping-rules');

		await expect(notFoundPage.heading).toBeVisible();

		await adminMappingRulesPage.mockClientConfig(createAdminClientConfigScript({isAdditionalIdpConfigured: 'true'}));
		await adminIndexPage.gotoSection('mapping-rules');

		await expect(adminIndexPage.sectionHeading('Mapping rules')).toBeVisible();
	});

	test('should keep Admin available when the additional IdP flag cannot be loaded on SaaS', async ({
		adminIndexPage,
		network,
		page,
	}) => {
		await adminIndexPage.seedSaasBootConfig({organizationId: SAAS_ORGANIZATION_ID, clusterId: SAAS_CLUSTER_ID});
		await page.route('**/admin/config.js', (route) => route.fulfill({status: 500}));
		network.use(...createSaasAdminHandlers());

		await adminIndexPage.gotoSection('mapping-rules');

		await expect(page.getByRole('heading', {name: 'Something went wrong'})).toBeVisible();
		await expect(adminIndexPage.navItem('Roles')).toBeVisible();
	});

	test('should show the access denied page on a section URL when admin access is denied', async ({
		adminIndexPage,
		page,
		network,
		componentAccessDeniedPage,
	}) => {
		network.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['tasklist']})),
			}),
		);

		await adminIndexPage.gotoSection('roles');

		await expect(page).toHaveURL('/admin/roles');
		await expect(componentAccessDeniedPage.heading).toBeVisible();
	});
});
