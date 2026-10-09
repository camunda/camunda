/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {createCurrentUser} from './api-mocks/current-user';
import {createLicense} from './api-mocks/license';
import {createSystemConfiguration} from './api-mocks/system-configuration';
import {createPaginatedResponse} from './api-mocks/shared';
import {createQueryProcessDefinitionsResponse} from './api-mocks/process-definitions';
import {createQueryProcessInstancesResponse} from './api-mocks/process-instances';
import {BPMN_XML} from './api-mocks/process-definition-xmls';
import {
	mockCurrentUserEndpoint,
	mockLicenseEndpoint,
	mockSystemConfigurationEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockGetProcessInstanceEndpoint,
	mockGetProcessInstanceCallHierarchyEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
	mockQueryProcessInstanceIncidentsEndpoint,
	mockGetProcessInstanceStatisticsEndpoint,
	mockGetProcessInstanceSequenceFlowsEndpoint,
	mockQueryAgentInstancesEndpoint,
	mockQueryProcessInstancesEndpoint,
	mockQueryElementInstancesEndpoint,
	mockQueryBatchOperationItemsEndpoint,
} from './mock-handlers';

function processInstanceHeaderHandlers(instance: ProcessInstance) {
	const empty = () => HttpResponse.json(createPaginatedResponse());
	return [
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(
				createCurrentUser({tenants: [{tenantId: instance.tenantId, name: 'Tenant A', description: null}]}),
			),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(
				createSystemConfiguration({
					components: {active: ['operate']},
					deployment: {isMultiTenancyEnabled: true, isTenantsApiEnabled: false, maxRequestSize: 0},
				}),
			),
		}),
		mockGetProcessDefinitionInstanceStatisticsEndpoint({successResponse: empty()}),
		mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({successResponse: empty()}),
		mockQueryProcessDefinitionsEndpoint({successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse())}),
		mockQueryProcessInstancesEndpoint({successResponse: HttpResponse.json(createQueryProcessInstancesResponse())}),
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
		mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json(instance)}),
		mockGetProcessInstanceCallHierarchyEndpoint({successResponse: HttpResponse.json([])}),
		mockGetProcessInstanceWaitStateStatisticsEndpoint({successResponse: empty()}),
		mockQueryProcessInstanceIncidentsEndpoint({successResponse: empty()}),
		mockGetProcessInstanceStatisticsEndpoint({successResponse: empty()}),
		mockGetProcessInstanceSequenceFlowsEndpoint({successResponse: empty()}),
		mockQueryAgentInstancesEndpoint({successResponse: empty()}),
		mockQueryElementInstancesEndpoint({successResponse: empty()}),
		mockQueryBatchOperationItemsEndpoint({successResponse: empty()}),
	];
}

export {processInstanceHeaderHandlers};
