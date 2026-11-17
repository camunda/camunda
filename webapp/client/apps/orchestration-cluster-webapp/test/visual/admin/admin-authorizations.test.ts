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
	mockQueryAuthorizationsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createAuthorization, createQueryAuthorizationsResponse} from '#/shared-test-modules/api-mocks/authorizations';

test.beforeEach(async ({network, adminAuthorizationsPage}) => {
	await adminAuthorizationsPage.mockClientConfig();
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
		mockQueryAuthorizationsEndpoint({
			successResponse: HttpResponse.json(
				createQueryAuthorizationsResponse({
					items: [
						createAuthorization({ownerId: 'john.doe', resourceId: 'order-process'}),
						createAuthorization({
							authorizationKey: '2251799813685250',
							ownerType: 'ROLE',
							ownerId: 'admin',
							resourceId: '*',
						}),
					],
				}),
			),
		}),
	);
});

test('should match the authorizations page snapshot', async ({adminAuthorizationsPage, page}) => {
	await adminAuthorizationsPage.goto('?resourceType=PROCESS_DEFINITION');
	await expect(adminAuthorizationsPage.row('john.doe')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the create authorization modal snapshot', async ({adminAuthorizationsPage, page}) => {
	await adminAuthorizationsPage.goto('?resourceType=PROCESS_DEFINITION');
	await adminAuthorizationsPage.addButton.click();

	await expect(adminAuthorizationsPage.addModal.dialog).toBeVisible();
	await expect(adminAuthorizationsPage.addModal.permissionCheckbox('READ_PROCESS_DEFINITION')).toBeVisible();
	await expect(page).toHaveScreenshot();
});
