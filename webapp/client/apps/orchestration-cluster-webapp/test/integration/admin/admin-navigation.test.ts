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
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';

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

test.describe('Admin navigation', () => {
	test('should render the admin navigation items', async ({adminIndexPage}) => {
		await adminIndexPage.goto();

		await expect(adminIndexPage.navItem('Users')).toBeVisible();
		await expect(adminIndexPage.navItem('Groups')).toBeVisible();
		await expect(adminIndexPage.navItem('Roles')).toBeVisible();
		await expect(adminIndexPage.navItem('Authorizations')).toBeVisible();
		await expect(adminIndexPage.navItem('Global user task listeners')).toBeVisible();
		await expect(adminIndexPage.navItem('Cluster variables')).toBeVisible();
		await expect(adminIndexPage.navItem('MCP Processes')).toBeVisible();
		await expect(adminIndexPage.navItem('Operations Log')).toBeVisible();
	});

	test('should navigate to a section and mark only it as the current page', async ({adminIndexPage, page}) => {
		await adminIndexPage.goto();

		await adminIndexPage.navItem('Roles').click();

		await expect(page).toHaveURL('/admin/roles');
		await expect(adminIndexPage.sectionHeading('Roles')).toBeVisible();
		await expect(adminIndexPage.navItem('Roles')).toHaveAttribute('aria-current', 'page');
		await expect(adminIndexPage.navItem('Groups')).not.toHaveAttribute('aria-current', 'page');
	});

	test('should hide tenants until multi-tenancy is enabled', async ({adminIndexPage, network}) => {
		await adminIndexPage.goto();

		await expect(adminIndexPage.navItem('Tenants')).toBeHidden();

		network.use(
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(
					createSystemConfiguration({
						components: {active: ['admin']},
						deployment: {isMultiTenancyEnabled: true, maxRequestSize: 0},
					}),
				),
			}),
		);
		await adminIndexPage.goto();

		await expect(adminIndexPage.navItem('Tenants')).toBeVisible();
	});

	test('should offer mapping rules instead of users when login is delegated', async ({adminIndexPage, network}) => {
		network.use(
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(
					createSystemConfiguration({
						components: {active: ['admin']},
						authentication: {canLogout: true, isLoginDelegated: true},
					}),
				),
			}),
		);

		await adminIndexPage.goto();

		await expect(adminIndexPage.navItem('Mapping rules')).toBeVisible();
		await expect(adminIndexPage.navItem('Users')).toBeHidden();
	});
});
