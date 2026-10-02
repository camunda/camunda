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
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockLicenseEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
	mockSystemConfigurationEndpoint,
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
	createQueryBatchOperationItemsResponse,
} from '#/shared-test-modules/api-mocks/batch-operations';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {movePointerAwayFromNavigation} from './movePointerAwayFromNavigation';

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
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [
						createProcessInstance({processInstanceKey: '2251799813685280', processDefinitionName: 'Order Process'}),
						createProcessInstance({
							processInstanceKey: '2251799813685281',
							processDefinitionName: 'Order Process',
							state: 'ACTIVE',
							hasIncident: true,
						}),
					],
				}),
			),
		}),
		mockQueryBatchOperationItemsEndpoint({
			successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse({items: []})),
		}),
	);
});

test('should match the processes page filters panel snapshot', async ({operateProcessesPage, page}) => {
	await operateProcessesPage.goto();
	await expect(operateProcessesPage.filtersPanel).toBeVisible();
	await expect(operateProcessesPage.instancesTable).toBeVisible();

	await movePointerAwayFromNavigation(page);
	await expect(page).toHaveScreenshot();
});

test('should show expanded failed operation details @desktop', async ({network, operateProcessesPage, page}) => {
	network.use(
		mockQueryBatchOperationItemsEndpoint({
			schema: z.object({filter: z.object({batchOperationKey: z.object({$eq: z.string()})})}),
			successResponse: HttpResponse.json(
				createQueryBatchOperationItemsResponse({
					items: [
						createBatchOperationItem({
							itemKey: 'incident-1',
							processInstanceKey: '2251799813685280',
							state: 'FAILED',
							errorMessage: 'Operation timed out',
						}),
						createBatchOperationItem({
							itemKey: 'incident-2',
							processInstanceKey: '2251799813685280',
							state: 'FAILED',
							errorMessage: 'Retry exhausted',
						}),
						createBatchOperationItem({
							itemKey: 'incident-3',
							processInstanceKey: '2251799813685280',
							state: 'FAILED',
						}),
						createBatchOperationItem({itemKey: 'item-4', processInstanceKey: '2251799813685281', state: 'COMPLETED'}),
					],
				}),
			),
			failureResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
		}),
	);
	await operateProcessesPage.goto('?batchOperationKey=2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee');
	await page.getByRole('button', {name: 'Show failure details for instance 2251799813685280'}).click();
	await expect(page.getByRole('row').filter({hasText: 'Operation timed out'})).toContainText(
		/Operation timed out\s+Retry exhausted\s+Operation failed/,
	);

	await movePointerAwayFromNavigation(page);
	await expect(operateProcessesPage.instancesTable).toHaveScreenshot('failed-operation-details.png');
});
