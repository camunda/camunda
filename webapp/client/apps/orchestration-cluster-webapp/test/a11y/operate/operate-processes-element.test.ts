/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {http, HttpResponse} from 'msw';
import {
	endpoints as apiEndpoints,
	queryProcessDefinitionsRequestBodySchema,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {test, expect} from '#/pw-modules/test-extend';
import {
	mockCurrentUserEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockGetProcessDefinitionStatisticsEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockLicenseEndpoint,
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
import {createGetProcessDefinitionStatisticsResponse} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';
import {createQueryProcessInstancesResponse} from '#/shared-test-modules/api-mocks/process-instances';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
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
			successResponse: HttpResponse.json(createQueryProcessInstancesResponse()),
		}),
		mockGetProcessDefinitionStatisticsEndpoint({
			successResponse: HttpResponse.json(createGetProcessDefinitionStatisticsResponse([])),
		}),
	);
});

test('should keep the selected element filter accessible', async ({network, operateProcessesPage, makeAxeBuilder}) => {
	network.use(mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}));
	await operateProcessesPage.goto('?process=order-process&version=1&elementId=task-1');
	await expect(operateProcessesPage.elementCombobox).toHaveValue('Review invoice');
	expect((await makeAxeBuilder().include('[aria-label="Filter"]').analyze()).violations).toEqual([]);
});

test('should expose an accessible XML failure and recovery action', async ({
	network,
	operateProcessesPage,
	makeAxeBuilder,
}) => {
	network.use(mockGetProcessDefinitionXmlEndpoint({successResponse: new HttpResponse(null, {status: 503})}));
	await operateProcessesPage.goto('?process=order-process&version=1&elementId=task-1');
	await expect(operateProcessesPage.elementCombobox).toBeDisabled();
	await expect(operateProcessesPage.elementCombobox).toHaveValue('task-1');
	await expect(operateProcessesPage.filtersPanel.getByRole('button', {name: 'Retry loading elements'})).toBeVisible({
		timeout: 15000,
	});
	await expect(operateProcessesPage.filtersPanel.getByRole('button', {name: 'Clear element filter'})).toBeVisible();
	expect((await makeAxeBuilder().include('[aria-label="Filter"]').analyze()).violations).toEqual([]);
});

test('should expose an accessible selected-definition lookup failure', async ({
	network,
	operateProcessesPage,
	makeAxeBuilder,
}) => {
	network.use(
		http.post(apiEndpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
			const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
			const processFilter = body.filter?.processDefinitionId;
			return (typeof processFilter === 'string' ? processFilter : processFilter?.$eq) === 'order-process'
				? new HttpResponse(null, {status: 503})
				: HttpResponse.json(
						createQueryProcessDefinitionsResponse({
							items: [createProcessDefinition({name: 'Order Process', processDefinitionId: 'order-process'})],
						}),
					);
		}),
	);
	await operateProcessesPage.goto('?process=order-process&version=1&elementId=task-1');
	await expect(operateProcessesPage.elementCombobox).toBeDisabled();
	await expect(operateProcessesPage.elementCombobox).toHaveValue('task-1');
	await expect(operateProcessesPage.filtersPanel.getByRole('button', {name: 'Retry loading elements'})).toBeVisible();
	expect((await makeAxeBuilder().include('[aria-label="Filter"]').analyze()).violations).toEqual([]);
});
