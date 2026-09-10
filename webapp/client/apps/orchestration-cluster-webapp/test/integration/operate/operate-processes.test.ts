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
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockLicenseEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
	mockSystemConfigurationEndpoint,
	mockCreateCancellationBatchOperationEndpoint,
	mockGetBatchOperationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createBatchOperationItem,
	createBatchOperation,
	createQueryBatchOperationItemsResponse,
} from '#/shared-test-modules/api-mocks/batch-operations';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser()),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
		mockGetProcessDefinitionInstanceStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [createProcessDefinition({name: 'Order Process', processDefinitionId: 'order-process'})],
				}),
			),
		}),
		mockQueryBatchOperationItemsEndpoint({
			successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
		}),
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [
						createProcessInstance({processInstanceKey: '1001', processDefinitionName: 'Order Process'}),
						createProcessInstance({processInstanceKey: '1002', processDefinitionName: 'Payment Process'}),
					],
				}),
			),
		}),
	);
});

test.describe('Operate processes page', () => {
	test('should cancel all matching instances and discard selection after acceptance', async ({
		network,
		page,
		operateProcessesPage,
	}) => {
		network.use(
			mockCreateCancellationBatchOperationEndpoint({
				successResponse: HttpResponse.json(
					{batchOperationKey: 'batch-op-1', batchOperationType: 'CANCEL_PROCESS_INSTANCE'},
					{status: 202},
				),
			}),
			mockGetBatchOperationEndpoint({successResponse: HttpResponse.json(createBatchOperation())}),
		);
		await operateProcessesPage.goto();
		await page.getByRole('checkbox', {name: 'Select all items'}).check({force: true});
		await page.getByRole('button', {name: 'Cancel', exact: true}).click();
		await expect(page.getByRole('dialog')).toContainText(
			'In case there are called instances, these will be canceled too.',
		);
		const submitted = page.waitForRequest(
			(request) => request.method() === 'POST' && request.url().endsWith('/v2/process-instances/cancellation'),
		);
		await page.getByRole('dialog').getByRole('button', {name: 'Apply'}).click();
		expect((await submitted).postDataJSON()).toEqual({
			filter: {
				$or: [
					{state: {$eq: 'ACTIVE'}, hasIncident: false},
					{state: {$eq: 'SUSPENDED'}},
					{hasIncident: true, state: {$neq: 'SUSPENDED'}},
				],
			},
		});
		await expect(page.getByText('The batch operation "Cancel Process Instance" has been started')).toBeVisible();
		await expect(page.getByRole('checkbox', {name: 'Select all items'})).not.toBeChecked();
	});

	test('should render the filters panel with the process combobox', async ({operateProcessesPage}) => {
		await operateProcessesPage.goto();

		await expect(operateProcessesPage.filtersPanel).toBeVisible();
		await expect(operateProcessesPage.processCombobox).toBeVisible();
	});

	test('should render the reset filters button disabled by default', async ({operateProcessesPage}) => {
		await operateProcessesPage.goto();

		await expect(operateProcessesPage.resetFiltersButton).toBeDisabled();
	});

	test('should list the matching process instances and link each one to its details page', async ({
		operateProcessesPage,
	}) => {
		await operateProcessesPage.goto();

		await expect(operateProcessesPage.instancesTable).toBeVisible();
		await expect(operateProcessesPage.instanceLink('1001')).toHaveAttribute('href', '/operate/processes/1001');
		await expect(operateProcessesPage.instanceLink('1002')).toHaveAttribute('href', '/operate/processes/1002');
	});

	test('should show operation states when filtering by a batch operation', async ({network, operateProcessesPage}) => {
		network.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [createBatchOperationItem({processInstanceKey: '1001', state: 'ACTIVE'})],
					}),
				),
			}),
		);

		await operateProcessesPage.goto('?batchOperationKey=2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee');

		await expect(operateProcessesPage.operationStateColumn).toBeVisible();
		await expect(operateProcessesPage.operationState('ACTIVE')).toBeVisible();
	});
});
