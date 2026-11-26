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
	mockCreateAdminUserEndpoint,
	mockCurrentUserEndpoint,
	mockLicenseEndpoint,
	mockQueryUsersEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUsersResponse} from '#/shared-test-modules/api-mocks/users';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['admin']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
	);
});

test('should create the first admin from trimmed input and continue to the Admin login', async ({
	network,
	page,
	adminSetupPage,
	adminLoginPage,
}) => {
	network.use(
		mockCreateAdminUserEndpoint({
			schema: z.strictObject({username: z.literal('first-admin'), password: z.literal('secret123')}),
			failureResponse: new HttpResponse(null, {status: 400}),
			successResponse: HttpResponse.json({username: 'first-admin'}, {status: 201}),
		}),
	);

	await adminSetupPage.goto();
	await expect(adminSetupPage.title).toBeVisible();
	await adminSetupPage.fillRequiredFields('  first-admin  ', 'secret123');
	await adminSetupPage.nameInput.fill('   ');
	await adminSetupPage.submitButton.click();

	await expect(page).toHaveURL('/admin/login');
	await expect(adminLoginPage.usernameInput).toBeVisible();
});

test('should show the backend error inline when an admin already exists', async ({network, page, adminSetupPage}) => {
	network.use(
		mockCreateAdminUserEndpoint({
			successResponse: HttpResponse.json(
				{
					type: 'about:blank',
					title: 'FORBIDDEN',
					status: 403,
					detail: 'An admin user already exists',
					instance: '/v2/setup/user',
				},
				{status: 403},
			),
		}),
	);

	await adminSetupPage.goto();
	await adminSetupPage.fillRequiredFields('first-admin', 'secret123');
	await adminSetupPage.submitButton.click();

	await expect(adminSetupPage.errorMessage('An admin user already exists')).toBeVisible();
	await expect(page).toHaveURL('/admin/setup');
});

test('should block submission when the passwords differ', async ({adminSetupPage}) => {
	await adminSetupPage.goto();
	await adminSetupPage.usernameInput.fill('first-admin');
	await adminSetupPage.passwordInput.fill('secret123');
	await adminSetupPage.confirmPasswordInput.fill('secret456');
	await adminSetupPage.submitButton.click();

	await expect(adminSetupPage.passwordMismatchError).toBeVisible();
});

test('should send a signed-in user to Admin instead of showing the setup form', async ({
	network,
	page,
	adminSetupPage,
}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockQueryUsersEndpoint({successResponse: HttpResponse.json(createQueryUsersResponse())}),
	);

	await adminSetupPage.goto();

	await expect(page).toHaveURL('/admin/users');
	await expect(adminSetupPage.title).toBeHidden();
});
