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
	test('should open the custom filters modal from the expanded panel', async ({shadcnTasklistIndexPage}) => {
		await shadcnTasklistIndexPage.goto();
		await shadcnTasklistIndexPage.expandFilters();

		await shadcnTasklistIndexPage.newFilterButton.click();

		await expect(shadcnTasklistIndexPage.customFiltersModal.dialog).toBeVisible();
		await expect(shadcnTasklistIndexPage.customFiltersModal.heading).toBeVisible();
	});

	test('should open the custom filters modal from the collapsed panel filter button', async ({
		shadcnTasklistIndexPage,
	}) => {
		await shadcnTasklistIndexPage.goto();

		await shadcnTasklistIndexPage.filterTasksButton.click();

		await expect(shadcnTasklistIndexPage.customFiltersModal.dialog).toBeVisible();
	});

	test('should create and apply a custom filter showing its link in the panel', async ({
		network,
		page,
		shadcnTasklistIndexPage,
	}) => {
		await shadcnTasklistIndexPage.goto();
		await shadcnTasklistIndexPage.expandFilters();
		await shadcnTasklistIndexPage.newFilterButton.click();

		network.use(
			mockQueryUserTasksEndpoint({
				schema: z.object({
					filter: z.object({
						state: z.literal('COMPLETED'),
						businessId: z.object({$eq: z.literal('ORDER-2024-0042')}),
					}),
					sort: z.tuple([z.object({field: z.literal('creationDate'), order: z.literal('desc')})]),
					page: z.object({limit: z.literal(50), from: z.literal(0)}),
				}),
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({
						items: [createUserTask({userTaskKey: '1', name: 'Custom filtered task', businessId: 'ORDER-2024-0042'})],
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await shadcnTasklistIndexPage.customFiltersModal.statusOption('Completed').click();
		await shadcnTasklistIndexPage.customFiltersModal.advancedFiltersToggle.click();
		await shadcnTasklistIndexPage.customFiltersModal.businessIdField.fill('ORDER-2024-0042');
		await shadcnTasklistIndexPage.customFiltersModal.applyButton.click();

		await expect(shadcnTasklistIndexPage.customFiltersModal.dialog).not.toBeVisible();
		await expect(shadcnTasklistIndexPage.customFilterLink('Custom')).toBeVisible();
		await expect(shadcnTasklistIndexPage.taskItem('Custom filtered task')).toBeVisible();

		const params = new URL(page.url()).searchParams;
		expect(params.get('filter')).toBe('custom');
		expect(params.get('state')).toBe('COMPLETED');
		expect(params.get('businessId')).toBe('eq_ORDER-2024-0042');
	});

	test('should save a named custom filter and show its link', async ({shadcnTasklistIndexPage}) => {
		await shadcnTasklistIndexPage.goto();
		await shadcnTasklistIndexPage.expandFilters();
		await shadcnTasklistIndexPage.newFilterButton.click();

		await shadcnTasklistIndexPage.customFiltersModal.statusOption('Completed').click();
		await shadcnTasklistIndexPage.customFiltersModal.saveButton.click();

		await shadcnTasklistIndexPage.filterNameModal.nameInput.fill('My Saved Filter');
		await shadcnTasklistIndexPage.filterNameModal.saveAndApplyButton.click();

		await expect(shadcnTasklistIndexPage.customFiltersModal.dialog).not.toBeVisible();
		await expect(shadcnTasklistIndexPage.customFilterLink('My Saved Filter')).toBeVisible();
	});

	test('should edit an existing custom filter with prefilled fields', async ({shadcnTasklistIndexPage}) => {
		await shadcnTasklistIndexPage.seedCustomFilters({
			custom: {assignee: 'all', status: 'completed', bpmnProcess: 'order-1'},
		});

		await shadcnTasklistIndexPage.goto();
		await shadcnTasklistIndexPage.expandFilters();

		await expect(shadcnTasklistIndexPage.customFilterLink('Custom')).toBeVisible();

		await shadcnTasklistIndexPage.customFilterActionsButton.click();
		await shadcnTasklistIndexPage.customFilterOverflowItem('Edit').click();

		await expect(shadcnTasklistIndexPage.customFiltersModal.dialog).toBeVisible();
		await expect(shadcnTasklistIndexPage.customFiltersModal.statusRadio('Completed')).toBeChecked();
	});

	test('should delete a custom filter and redirect to all-open when active', async ({shadcnTasklistIndexPage}) => {
		await shadcnTasklistIndexPage.seedCustomFilters({custom: {assignee: 'all', status: 'completed'}});

		await shadcnTasklistIndexPage.goto('?filter=custom&state=COMPLETED');
		await shadcnTasklistIndexPage.expandFilters();

		await expect(shadcnTasklistIndexPage.customFilterLink('Custom')).toBeVisible();

		await shadcnTasklistIndexPage.customFilterActionsButton.click();
		await shadcnTasklistIndexPage.customFilterOverflowItem('Delete').click();

		await expect(shadcnTasklistIndexPage.deleteFilterModal.dialog).toBeVisible();
		await shadcnTasklistIndexPage.deleteFilterModal.confirmButton.click();

		await expect(shadcnTasklistIndexPage.deleteFilterModal.dialog).not.toBeVisible();
		await expect(shadcnTasklistIndexPage.customFilterLink('Custom')).not.toBeVisible();
		await expect(shadcnTasklistIndexPage.tasksPanelHeading('All open tasks')).toBeVisible();
	});

	test('should include tenantId in the URL for a custom filter with a tenant', async ({
		page,
		shadcnTasklistIndexPage,
	}) => {
		await shadcnTasklistIndexPage.seedCustomFilters({custom: {assignee: 'all', status: 'all', tenant: '<default>'}});

		await shadcnTasklistIndexPage.goto();
		await shadcnTasklistIndexPage.expandFilters();

		await shadcnTasklistIndexPage.customFilterLink('Custom').click();

		const params = new URL(page.url()).searchParams;
		expect(params.get('filter')).toBe('custom');
		expect(params.get('tenantId')).toBe('<default>');
	});

	test('should populate the process select in the modal from process definitions', async ({
		shadcnTasklistIndexPage,
	}) => {
		await shadcnTasklistIndexPage.goto();
		await shadcnTasklistIndexPage.expandFilters();
		await shadcnTasklistIndexPage.newFilterButton.click();

		await expect(shadcnTasklistIndexPage.customFiltersModal.processSelect).toBeVisible();
		await shadcnTasklistIndexPage.customFiltersModal.processSelect.click();
		await expect(shadcnTasklistIndexPage.customFiltersModal.processOption('All processes')).toBeVisible();
		await expect(shadcnTasklistIndexPage.customFiltersModal.processOption('Order Process - v1')).toBeVisible();
		await expect(shadcnTasklistIndexPage.customFiltersModal.processOption('Payment Process - v2')).toBeVisible();
	});
});
