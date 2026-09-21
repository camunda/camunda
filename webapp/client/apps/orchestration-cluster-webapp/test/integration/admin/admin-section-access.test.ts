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

test.describe('Admin section access', () => {
	test('should not serve tenants until multi-tenancy is enabled', async ({adminIndexPage, notFoundPage, network}) => {
		await adminIndexPage.gotoSection('tenants');

		await expect(notFoundPage.heading).toBeVisible();

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
		await adminIndexPage.gotoSection('tenants');

		await expect(adminIndexPage.sectionHeading('Tenants')).toBeVisible();
	});

	test('should not serve mapping rules while Camunda owns the users', async ({adminIndexPage, notFoundPage}) => {
		await adminIndexPage.gotoSection('mapping-rules');

		await expect(notFoundPage.heading).toBeVisible();
	});

	test('should serve mapping rules instead of users when login is delegated', async ({
		adminIndexPage,
		notFoundPage,
		network,
	}) => {
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

		await adminIndexPage.gotoSection('mapping-rules');

		await expect(adminIndexPage.sectionHeading('Mapping rules')).toBeVisible();

		await adminIndexPage.gotoSection('users');

		await expect(notFoundPage.heading).toBeVisible();
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
