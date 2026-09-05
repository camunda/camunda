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
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
	mockQueryBatchOperationItemsEndpoint,
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
import {createQueryBatchOperationItemsResponse} from '#/shared-test-modules/api-mocks/batch-operations';
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
});
