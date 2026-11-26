/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {
	mockCurrentUserEndpoint,
	mockGetProcessDefinitionStatisticsEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockLicenseEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {
	createGetProcessDefinitionStatisticsResponse,
	createProcessDefinitionStatistic,
} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {BPMN_XML as MOVE_BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';

const defaultOperatePreviewProcessesHandlers = [
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
];

const batchMoveScenarioHandlers = [
	mockQueryProcessDefinitionsEndpoint({
		successResponse: HttpResponse.json(
			createQueryProcessDefinitionsResponse({
				items: [
					createProcessDefinition({
						processDefinitionId: 'my_simple_process',
						tenantId: 'tenant-a',
						name: 'Invoice process',
						version: 1,
					}),
					createProcessDefinition({
						processDefinitionId: 'true',
						tenantId: 'tenant-a',
						name: 'Boolean process',
						version: 1,
					}),
				],
			}),
		),
	}),
	mockQueryProcessInstancesEndpoint({
		successResponse: HttpResponse.json(
			createQueryProcessInstancesResponse({
				items: [createProcessInstance({processInstanceKey: '1', state: 'ACTIVE'})],
			}),
		),
	}),
	mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(MOVE_BPMN_XML)}),
	mockGetProcessDefinitionStatisticsEndpoint({
		successResponse: HttpResponse.json(
			createGetProcessDefinitionStatisticsResponse([
				createProcessDefinitionStatistic({elementId: 'task-1', active: 1}),
			]),
		),
	}),
];

export {batchMoveScenarioHandlers, defaultOperatePreviewProcessesHandlers};
