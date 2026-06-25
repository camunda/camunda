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
	mockQueryProcessDefinitionsEndpoint,
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';

const currentUser = createCurrentUser({username: 'demo'});

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(currentUser)}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryUserTasksEndpoint({successResponse: HttpResponse.json(createQueryUserTasksResponse())}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [
						createProcessDefinition({name: 'Order Process', processDefinitionKey: 'order-1', version: 1}),
						createProcessDefinition({name: 'Payment Process', processDefinitionKey: 'payment-1', version: 2}),
					],
				}),
			),
		}),
	);
});

test.describe('Custom filters', () => {
	test('should open the custom filters modal from the expanded panel', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.goto();
		await tasklistIndexPage.expandFilters();

		await tasklistIndexPage.newFilterButton.click();

		await expect(tasklistIndexPage.customFiltersModal.dialog).toBeVisible();
		await expect(tasklistIndexPage.customFiltersModal.heading).toBeVisible();
	});

	test('should open the custom filters modal from the collapsed panel filter button', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.goto();

		await tasklistIndexPage.filterTasksButton.click();

		await expect(tasklistIndexPage.customFiltersModal.dialog).toBeVisible();
	});

	test('should create and apply a custom filter showing its link in the panel', async ({
		network,
		page,
		tasklistIndexPage,
	}) => {
		await tasklistIndexPage.goto();
		await tasklistIndexPage.expandFilters();
		await tasklistIndexPage.newFilterButton.click();

		network.use(
			mockQueryUserTasksEndpoint({
				schema: z.object({
					filter: z.object({
						state: z.literal('COMPLETED'),
					}),
					sort: z.tuple([z.object({field: z.literal('creationDate'), order: z.literal('desc')})]),
					page: z.object({limit: z.literal(50), from: z.literal(0)}),
				}),
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({
						items: [createUserTask({userTaskKey: '1', name: 'Custom filtered task'})],
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await tasklistIndexPage.customFiltersModal.statusOption('Completed').click();
		await tasklistIndexPage.customFiltersModal.applyButton.click();

		await expect(tasklistIndexPage.customFiltersModal.dialog).not.toBeVisible();
		await expect(tasklistIndexPage.customFilterLink('Custom')).toBeVisible();
		await expect(tasklistIndexPage.taskItem('Custom filtered task')).toBeVisible();

		const params = new URL(page.url()).searchParams;
		expect(params.get('filter')).toBe('custom');
		expect(params.get('state')).toBe('COMPLETED');
	});

	test('should save a named custom filter and show its link', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.goto();
		await tasklistIndexPage.expandFilters();
		await tasklistIndexPage.newFilterButton.click();

		await tasklistIndexPage.customFiltersModal.statusOption('Completed').click();
		await tasklistIndexPage.customFiltersModal.saveButton.click();

		await tasklistIndexPage.filterNameModal.nameInput.fill('My Saved Filter');
		await tasklistIndexPage.filterNameModal.saveAndApplyButton.click();

		await expect(tasklistIndexPage.customFiltersModal.dialog).not.toBeVisible();
		await expect(tasklistIndexPage.customFilterLink('My Saved Filter')).toBeVisible();
	});

	test('should edit an existing custom filter with prefilled fields', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.seedCustomFilters({custom: {assignee: 'all', status: 'completed', bpmnProcess: 'order-1'}});

		await tasklistIndexPage.goto();
		await tasklistIndexPage.expandFilters();

		await expect(tasklistIndexPage.customFilterLink('Custom')).toBeVisible();

		await tasklistIndexPage.customFilterActionsButton.click();
		await tasklistIndexPage.customFilterOverflowItem('Edit').click();

		await expect(tasklistIndexPage.customFiltersModal.dialog).toBeVisible();
		await expect(tasklistIndexPage.customFiltersModal.statusRadio('Completed')).toBeChecked();
	});

	test('should delete a custom filter and redirect to all-open when active', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.seedCustomFilters({custom: {assignee: 'all', status: 'completed'}});

		await tasklistIndexPage.goto('?filter=custom&state=COMPLETED');
		await tasklistIndexPage.expandFilters();

		await expect(tasklistIndexPage.customFilterLink('Custom')).toBeVisible();

		await tasklistIndexPage.customFilterActionsButton.click();
		await tasklistIndexPage.customFilterOverflowItem('Delete').click();

		await expect(tasklistIndexPage.deleteFilterModal.dialog).toBeVisible();
		await tasklistIndexPage.deleteFilterModal.confirmButton.click();

		await expect(tasklistIndexPage.deleteFilterModal.dialog).not.toBeVisible();
		await expect(tasklistIndexPage.customFilterLink('Custom')).not.toBeVisible();
		await expect(tasklistIndexPage.tasksPanelHeading('All open tasks')).toBeVisible();
	});

	test('should include tenantId in the URL for a custom filter with a tenant', async ({page, tasklistIndexPage}) => {
		await tasklistIndexPage.seedCustomFilters({custom: {assignee: 'all', status: 'all', tenant: '<default>'}});

		await tasklistIndexPage.goto();
		await tasklistIndexPage.expandFilters();

		await tasklistIndexPage.customFilterLink('Custom').click();

		const params = new URL(page.url()).searchParams;
		expect(params.get('filter')).toBe('custom');
		expect(params.get('tenantId')).toBe('<default>');
	});

	test('should populate the process select in the modal from process definitions', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.goto();
		await tasklistIndexPage.expandFilters();
		await tasklistIndexPage.newFilterButton.click();

		await expect(tasklistIndexPage.customFiltersModal.processSelect).toBeVisible();
		await expect(tasklistIndexPage.customFiltersModal.processSelect).toContainText('All processes');
		await expect(tasklistIndexPage.customFiltersModal.processSelect).toContainText('Order Process');
		await expect(tasklistIndexPage.customFiltersModal.processSelect).toContainText('Payment Process');
	});
});
