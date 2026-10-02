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
	mockSearchGlobalTaskListenersEndpoint,
	mockCreateGlobalTaskListenerEndpoint,
	mockUpdateGlobalTaskListenerEndpoint,
	mockDeleteGlobalTaskListenerEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createGlobalTaskListener,
	createQueryGlobalTaskListenersResponse,
} from '#/shared-test-modules/api-mocks/global-task-listeners';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['admin']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
	);
});

test.describe('Admin global task listeners', () => {
	test('should list global task listeners', async ({adminGlobalTaskListenersPage, network}) => {
		network.use(
			mockSearchGlobalTaskListenersEndpoint({
				successResponse: HttpResponse.json(
					createQueryGlobalTaskListenersResponse({
						items: [createGlobalTaskListener({id: 'my-listener', type: 'my-type'})],
					}),
				),
			}),
		);

		await adminGlobalTaskListenersPage.goto();

		await expect(adminGlobalTaskListenersPage.row('my-listener')).toBeVisible();
	});

	test('should create a global task listener', async ({adminGlobalTaskListenersPage, network, page}) => {
		network.use(
			mockSearchGlobalTaskListenersEndpoint({
				successResponse: HttpResponse.json(createQueryGlobalTaskListenersResponse()),
			}),
		);

		await adminGlobalTaskListenersPage.goto();
		await adminGlobalTaskListenersPage.addButton.click();

		await expect(adminGlobalTaskListenersPage.addModal.dialog).toBeVisible();

		network.use(
			mockCreateGlobalTaskListenerEndpoint({
				schema: z.object({id: z.literal('my-listener'), type: z.literal('my-type')}),
				successResponse: HttpResponse.json({}),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockSearchGlobalTaskListenersEndpoint({
				successResponse: HttpResponse.json(
					createQueryGlobalTaskListenersResponse({
						items: [createGlobalTaskListener({id: 'my-listener', type: 'my-type'})],
					}),
				),
			}),
		);

		await adminGlobalTaskListenersPage.addModal.idInput.fill('my-listener');
		await adminGlobalTaskListenersPage.addModal.typeInput.fill('my-type');
		await adminGlobalTaskListenersPage.addModal.eventTypesCombobox.click();
		await page.getByRole('option', {name: 'Creating'}).click();
		await page.keyboard.press('Escape');
		await adminGlobalTaskListenersPage.addModal.saveButton.click();

		await expect(adminGlobalTaskListenersPage.addModal.dialog).not.toBeVisible();
		await expect(adminGlobalTaskListenersPage.row('my-listener')).toBeVisible();
	});

	test('should edit a global task listener with its fields prefilled', async ({
		adminGlobalTaskListenersPage,
		network,
	}) => {
		network.use(
			mockSearchGlobalTaskListenersEndpoint({
				successResponse: HttpResponse.json(
					createQueryGlobalTaskListenersResponse({
						items: [createGlobalTaskListener({id: 'my-listener', type: 'my-type'})],
					}),
				),
			}),
		);

		await adminGlobalTaskListenersPage.goto();
		await adminGlobalTaskListenersPage.rowActionsButton('my-listener').click();
		await adminGlobalTaskListenersPage.menuItem('Edit').click();

		await expect(adminGlobalTaskListenersPage.editModal.dialog).toBeVisible();
		await expect(adminGlobalTaskListenersPage.editModal.typeInput).toHaveValue('my-type');

		network.use(
			mockUpdateGlobalTaskListenerEndpoint({
				schema: z.object({type: z.literal('updated-type')}),
				successResponse: HttpResponse.json({}),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockSearchGlobalTaskListenersEndpoint({
				successResponse: HttpResponse.json(
					createQueryGlobalTaskListenersResponse({
						items: [createGlobalTaskListener({id: 'my-listener', type: 'updated-type'})],
					}),
				),
			}),
		);

		await adminGlobalTaskListenersPage.editModal.typeInput.fill('updated-type');
		await adminGlobalTaskListenersPage.editModal.saveButton.click();

		await expect(adminGlobalTaskListenersPage.editModal.dialog).not.toBeVisible();
		await expect(adminGlobalTaskListenersPage.row('updated-type')).toBeVisible();
	});

	test('should delete a global task listener', async ({adminGlobalTaskListenersPage, network}) => {
		network.use(
			mockSearchGlobalTaskListenersEndpoint({
				successResponse: HttpResponse.json(
					createQueryGlobalTaskListenersResponse({items: [createGlobalTaskListener({id: 'my-listener'})]}),
				),
			}),
		);

		await adminGlobalTaskListenersPage.goto();
		await adminGlobalTaskListenersPage.rowActionsButton('my-listener').click();
		await adminGlobalTaskListenersPage.menuItem('Delete').click();

		await expect(adminGlobalTaskListenersPage.deleteModal.dialog).toBeVisible();

		network.use(
			mockDeleteGlobalTaskListenerEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockSearchGlobalTaskListenersEndpoint({
				successResponse: HttpResponse.json(createQueryGlobalTaskListenersResponse()),
			}),
		);

		await adminGlobalTaskListenersPage.deleteModal.confirmButton.click();

		await expect(adminGlobalTaskListenersPage.deleteModal.dialog).not.toBeVisible();
		await expect(adminGlobalTaskListenersPage.row('my-listener')).not.toBeVisible();
	});

	test('should search global task listeners by ID', async ({adminGlobalTaskListenersPage, network}) => {
		network.use(
			mockSearchGlobalTaskListenersEndpoint({
				successResponse: HttpResponse.json(createQueryGlobalTaskListenersResponse()),
			}),
		);

		await adminGlobalTaskListenersPage.goto();
		await expect(adminGlobalTaskListenersPage.searchInput).toBeVisible();

		network.use(
			mockSearchGlobalTaskListenersEndpoint({
				schema: z.object({filter: z.object({id: z.object({$like: z.literal('*my-listener*')})})}),
				successResponse: HttpResponse.json(
					createQueryGlobalTaskListenersResponse({items: [createGlobalTaskListener({id: 'my-listener'})]}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await adminGlobalTaskListenersPage.searchInput.fill('my-listener');

		await expect(adminGlobalTaskListenersPage.row('my-listener')).toBeVisible();
	});
});
