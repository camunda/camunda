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
	mockQueryAuthorizationsEndpoint,
	mockGetAuthorizationEndpoint,
	mockCreateAuthorizationEndpoint,
	mockDeleteAuthorizationEndpoint,
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
	);
});

test.describe('Admin authorizations', () => {
	test('should list the authorizations of the first resource type by default', async ({
		adminAuthorizationsPage,
		network,
	}) => {
		network.use(
			mockQueryAuthorizationsEndpoint({
				schema: z.object({filter: z.object({resourceType: z.literal('AUDIT_LOG')})}),
				successResponse: HttpResponse.json(
					createQueryAuthorizationsResponse({
						items: [
							createAuthorization({
								ownerId: 'john.doe',
								resourceType: 'AUDIT_LOG',
								resourceId: '*',
								permissionTypes: ['READ', 'UPDATE'],
							}),
						],
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await adminAuthorizationsPage.goto();

		await expect(adminAuthorizationsPage.row('john.doe')).toContainText('READ, UPDATE');
	});

	test('should not flash the empty state while switching the resource type', async ({
		adminAuthorizationsPage,
		network,
	}) => {
		network.use(
			mockQueryAuthorizationsEndpoint({
				successResponse: HttpResponse.json(
					createQueryAuthorizationsResponse({items: [createAuthorization({ownerId: 'john.doe'})]}),
				),
			}),
		);
		await adminAuthorizationsPage.goto('?resourceType=PROCESS_DEFINITION');
		await expect(adminAuthorizationsPage.row('john.doe')).toBeVisible();

		network.use(
			mockQueryAuthorizationsEndpoint({
				schema: z.object({filter: z.object({resourceType: z.literal('USER_TASK')})}),
				delay: 1500,
				successResponse: HttpResponse.json(
					createQueryAuthorizationsResponse({
						items: [
							createAuthorization({
								ownerId: 'jane.doe',
								resourceType: 'USER_TASK',
								resourceId: null,
								resourcePropertyName: 'assignee',
								permissionTypes: ['READ'],
							}),
						],
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await adminAuthorizationsPage.resourceTypeSelect.click();
		await adminAuthorizationsPage.resourceTypeOption('USER_TASK').click();

		await expect(adminAuthorizationsPage.loadingSkeleton).toBeVisible();
		await expect(adminAuthorizationsPage.resourceTypeSelect).toHaveText('USER_TASK');
		await expect(adminAuthorizationsPage.ownerSearchInput).toBeVisible();
		await expect(adminAuthorizationsPage.addButton).toBeVisible();
		await expect(adminAuthorizationsPage.emptyState).not.toBeVisible();
		await expect(adminAuthorizationsPage.row('john.doe')).not.toBeVisible();
		await expect(adminAuthorizationsPage.row('jane.doe')).toContainText('assignee');
		await expect(adminAuthorizationsPage.loadingSkeleton).not.toBeVisible();
		await expect(adminAuthorizationsPage.emptyState).not.toBeVisible();
	});

	test('should filter by owner ID', async ({adminAuthorizationsPage, network}) => {
		network.use(
			mockQueryAuthorizationsEndpoint({successResponse: HttpResponse.json(createQueryAuthorizationsResponse())}),
		);
		await adminAuthorizationsPage.goto('?resourceType=PROCESS_DEFINITION');
		await expect(adminAuthorizationsPage.emptyState).toBeVisible();

		network.use(
			mockQueryAuthorizationsEndpoint({
				schema: z.object({filter: z.object({ownerId: z.literal('john.doe')})}),
				successResponse: HttpResponse.json(
					createQueryAuthorizationsResponse({items: [createAuthorization({ownerId: 'john.doe'})]}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await adminAuthorizationsPage.ownerSearchInput.fill('john.doe');

		await expect(adminAuthorizationsPage.row('john.doe')).toBeVisible();
	});

	test('should create an authorization', async ({adminAuthorizationsPage, network}) => {
		network.use(
			mockQueryAuthorizationsEndpoint({successResponse: HttpResponse.json(createQueryAuthorizationsResponse())}),
		);
		await adminAuthorizationsPage.goto('?resourceType=PROCESS_DEFINITION');
		await adminAuthorizationsPage.addButton.click();
		await expect(adminAuthorizationsPage.addModal.dialog).toBeVisible();

		const created = createAuthorization({
			authorizationKey: '42',
			ownerId: 'new.user',
			resourceId: 'order-process',
			permissionTypes: ['READ_PROCESS_DEFINITION'],
		});
		network.use(
			mockCreateAuthorizationEndpoint({
				schema: z.object({
					ownerType: z.literal('USER'),
					ownerId: z.literal('new.user'),
					resourceType: z.literal('PROCESS_DEFINITION'),
					resourceId: z.literal('order-process'),
					permissionTypes: z.tuple([z.literal('READ_PROCESS_DEFINITION')]),
				}),
				successResponse: HttpResponse.json({authorizationKey: '42'}),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockGetAuthorizationEndpoint({successResponse: HttpResponse.json(created)}),
			mockQueryAuthorizationsEndpoint({
				successResponse: HttpResponse.json(createQueryAuthorizationsResponse({items: [created]})),
			}),
		);

		await adminAuthorizationsPage.addModal.usernameInput.fill('new.user');
		await adminAuthorizationsPage.addModal.resourceIdInput.fill('order-process');
		await adminAuthorizationsPage.addModal.permissionCheckbox('READ_PROCESS_DEFINITION').click();
		await adminAuthorizationsPage.addModal.createButton.click();

		await expect(adminAuthorizationsPage.addModal.dialog).not.toBeVisible();
		await expect(adminAuthorizationsPage.row('new.user')).toBeVisible();
	});

	test('should delete an authorization', async ({adminAuthorizationsPage, network}) => {
		network.use(
			mockQueryAuthorizationsEndpoint({
				successResponse: HttpResponse.json(
					createQueryAuthorizationsResponse({items: [createAuthorization({ownerId: 'john.doe'})]}),
				),
			}),
		);
		await adminAuthorizationsPage.goto('?resourceType=PROCESS_DEFINITION');
		await adminAuthorizationsPage.deleteButton('john.doe').click();
		await expect(adminAuthorizationsPage.deleteModal.dialog).toBeVisible();

		network.use(
			mockDeleteAuthorizationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetAuthorizationEndpoint({successResponse: new HttpResponse(null, {status: 404})}),
			mockQueryAuthorizationsEndpoint({successResponse: HttpResponse.json(createQueryAuthorizationsResponse())}),
		);

		await adminAuthorizationsPage.deleteModal.confirmButton.click();

		await expect(adminAuthorizationsPage.deleteModal.dialog).not.toBeVisible();
		await expect(adminAuthorizationsPage.row('john.doe')).not.toBeVisible();
	});

	test('should not allow deleting the authorizations of a default role', async ({adminAuthorizationsPage, network}) => {
		network.use(
			mockQueryAuthorizationsEndpoint({
				successResponse: HttpResponse.json(
					createQueryAuthorizationsResponse({
						items: [createAuthorization({ownerType: 'ROLE', ownerId: 'admin'})],
					}),
				),
			}),
		);

		await adminAuthorizationsPage.goto('?resourceType=PROCESS_DEFINITION');

		await expect(adminAuthorizationsPage.deleteButton('admin')).toBeDisabled();
	});
});
