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
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createQueryProcessDefinitionsResponse} from '#/shared-test-modules/api-mocks/process-definitions';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['operate']})),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
		}),
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [
						createProcessInstance({processInstanceKey: '2251799813685249', processDefinitionName: 'Order process'}),
					],
				}),
			),
		}),
		mockQueryBatchOperationItemsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse({items: []})),
		}),
	);
});

test.describe('Operate Processes DS preview (/operate-preview/processes)', () => {
	test('should render the filters and the process instances table', async ({operatePreviewProcessesPage}) => {
		await operatePreviewProcessesPage.goto('?active=true');

		await expect(operatePreviewProcessesPage.heading).toBeAttached();
		await expect(operatePreviewProcessesPage.nameFilter).toBeVisible();
		await expect(operatePreviewProcessesPage.diagramPanelPlaceholder).toBeVisible();
		await expect(operatePreviewProcessesPage.instancesTable).toBeVisible();
		await expect(operatePreviewProcessesPage.instancesTable.getByText('Order process')).toBeVisible();
	});

	test('should keep the Carbon Processes route working', async ({network, operateProcessesPage, page}) => {
		network.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			}),
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryProcessInstancesResponse()),
			}),
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse({items: []})),
			}),
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse({items: []})),
			}),
		);
		await operateProcessesPage.goto();

		await expect(page.getByRole('combobox', {name: 'Name', exact: true})).toBeVisible();
	});

	test('should show an error state in the table when the search fails and recover on try again', async ({
		network,
		operatePreviewProcessesPage,
	}) => {
		network.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: new HttpResponse(null, {status: 500}),
			}),
		);
		await operatePreviewProcessesPage.goto('?active=true');
		await expect(operatePreviewProcessesPage.instancesTable.getByText("Couldn't fetch data")).toBeVisible({
			timeout: 15000,
		});

		network.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [
							createProcessInstance({processInstanceKey: '2251799813685249', processDefinitionName: 'Order process'}),
						],
					}),
				),
			}),
		);
		await operatePreviewProcessesPage.instancesTable.getByRole('button', {name: 'Try again'}).click();

		await expect(operatePreviewProcessesPage.instancesTable.getByText('Order process')).toBeVisible();
	});

	test('should show a permission state in the table when the search is forbidden', async ({
		network,
		operatePreviewProcessesPage,
	}) => {
		network.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json({}, {status: 403}),
			}),
		);
		await operatePreviewProcessesPage.goto('?active=true');

		await expect(operatePreviewProcessesPage.instancesTable.getByText('You need permission')).toBeVisible({
			timeout: 15000,
		});
		await expect(operatePreviewProcessesPage.instancesTable.getByRole('button', {name: 'Try again'})).toBeVisible();
	});
});
