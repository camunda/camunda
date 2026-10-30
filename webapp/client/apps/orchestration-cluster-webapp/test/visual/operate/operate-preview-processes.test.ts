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
		mockQueryBatchOperationItemsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse({items: []})),
		}),
	);
});

test('should match the instances table snapshot with active, incident, and completed instances', async ({
	network,
	operatePreviewProcessesPage,
	page,
}) => {
	network.use(
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [
						createProcessInstance({processInstanceKey: '2251799813700001', processDefinitionName: 'Order process'}),
						createProcessInstance({
							processInstanceKey: '2251799813700002',
							processDefinitionName: 'Shipping process',
							hasIncident: true,
							parentProcessInstanceKey: '2251799813700001',
						}),
						createProcessInstance({
							processInstanceKey: '2251799813700003',
							processDefinitionName: 'Invoice process',
							state: 'COMPLETED',
							endDate: '2026-01-16T10:00:00.000Z',
						}),
					],
				}),
			),
		}),
	);
	await page.goto(`${operatePreviewProcessesPage.processesUrl}?active=true&incidents=true&completed=true`);

	await expect(
		operatePreviewProcessesPage.instancesTable.getByRole('link', {name: 'View instance 2251799813700001'}),
	).toBeVisible();
	await expect(operatePreviewProcessesPage.instancesTable).toHaveScreenshot();
});

test('should match the instances table snapshot when no instances match', async ({
	network,
	operatePreviewProcessesPage,
	page,
}) => {
	network.use(
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(createQueryProcessInstancesResponse({items: []})),
		}),
	);
	await page.goto(`${operatePreviewProcessesPage.processesUrl}?active=true`);

	await expect(operatePreviewProcessesPage.instancesTable.getByText(/no Instances matching/)).toBeVisible();
	await expect(operatePreviewProcessesPage.instancesTable).toHaveScreenshot();
});
