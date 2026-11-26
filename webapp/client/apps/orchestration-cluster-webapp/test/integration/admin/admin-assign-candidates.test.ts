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
import {queryUsersRequestBodySchema} from '@camunda/camunda-api-zod-schemas/8.11';
import {
	mockCurrentUserEndpoint,
	mockGetGroupEndpoint,
	mockLicenseEndpoint,
	mockQueryGroupsEndpoint,
	mockQueryRolesByGroupEndpoint,
	mockQueryUsersByGroupEndpoint,
	mockQueryUsersEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createGroup, createPage, createQueryGroupsResponse} from '#/shared-test-modules/api-mocks/groups';
import {createUser} from '#/shared-test-modules/api-mocks/users';

const ENGINEERING = createGroup({groupId: 'engineering', name: 'Engineering'});
const OPERATIONS = createGroup({groupId: 'operations', name: 'Operations'});

test.describe('Admin assign candidates', () => {
	test.beforeEach(async ({adminGroupsPage, adminGroupDetailPage, network, page}) => {
		await adminGroupsPage.mockClientConfig();
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(
					createSystemConfiguration({
						components: {active: ['admin']},
						authentication: {canLogout: true, isLoginDelegated: false, isCamundaGroupsEnabled: true},
					}),
				),
			}),
			mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
			mockQueryGroupsEndpoint({
				successResponse: HttpResponse.json(createQueryGroupsResponse({items: [ENGINEERING, OPERATIONS]})),
			}),
			mockQueryRolesByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}),
			mockGetGroupEndpoint({successResponse: HttpResponse.json(ENGINEERING)}),
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(
					createPage([createUser({username: 'carol'}), createUser({username: 'dave'})]),
				),
			}),
		);

		await test.step('cache the candidates of the first group', async () => {
			await adminGroupDetailPage.goto('engineering');
			await adminGroupDetailPage.button('Assign user').click();
			await adminGroupDetailPage.dialogCombobox.click();

			await expect(adminGroupDetailPage.option('carol')).toBeVisible();
			await expect(adminGroupDetailPage.option('dave')).toBeVisible();

			await page.keyboard.press('Escape');
			await page.keyboard.press('Escape');
			await expect(adminGroupDetailPage.dialog).toBeHidden();
		});

		await test.step('open the second group from the list', async () => {
			network.use(
				mockGetGroupEndpoint({successResponse: HttpResponse.json(OPERATIONS)}),
				mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([{username: 'carol'}]))}),
			);

			await adminGroupDetailPage.breadcrumbLink('Groups').click();
			await adminGroupsPage.cell('operations').click();

			await expect(adminGroupDetailPage.heading('Operations')).toBeVisible();
			await expect(adminGroupDetailPage.cell('carol')).toBeVisible();
		});
	});

	test('should not offer the candidates of another group while the members of this group load', async ({
		adminGroupDetailPage,
		network,
	}) => {
		network.use(
			mockQueryUsersByGroupEndpoint({
				successResponse: HttpResponse.json(createPage([{username: 'carol'}])),
				delay: 'infinite',
			}),
		);

		await adminGroupDetailPage.button('Assign user').click();
		await adminGroupDetailPage.dialogCombobox.click();

		await expect(adminGroupDetailPage.noSuggestions).toBeVisible();
		await expect(adminGroupDetailPage.option('carol')).toBeHidden();
		await expect(adminGroupDetailPage.option('dave')).toBeHidden();
	});

	test('should offer only the candidates of this group once its members are loaded', async ({
		adminGroupDetailPage,
		network,
	}) => {
		network.use(
			mockQueryUsersEndpoint({
				schema: queryUsersRequestBodySchema.and(
					z.object({filter: z.object({username: z.object({$notIn: z.tuple([z.literal('carol')])})})}),
				),
				successResponse: HttpResponse.json(createPage([createUser({username: 'dave'})])),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await adminGroupDetailPage.button('Assign user').click();
		await adminGroupDetailPage.dialogCombobox.click();

		await expect(adminGroupDetailPage.option('dave')).toBeVisible();
		await expect(adminGroupDetailPage.option('carol')).toBeHidden();
	});
});
