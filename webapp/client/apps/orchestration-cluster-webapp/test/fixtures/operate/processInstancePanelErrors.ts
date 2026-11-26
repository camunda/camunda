/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import type {RequestHandler} from 'msw';
import {
	mockCurrentUserEndpoint,
	mockGetProcessInstanceEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
	mockLicenseEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {processInstanceDiagramHandlers} from '#/shared-test-modules/process-instance-diagram-handlers';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';

const PROCESS_INSTANCE_ID = '2251799813685280';

const processInstancePanelErrorBaseHandlers: RequestHandler[] = [
	...processInstanceDiagramHandlers(),
	...processInstanceHeaderHandlers(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})),
	mockCurrentUserEndpoint({
		successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['operate']})),
	}),
	mockSystemConfigurationEndpoint({
		successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
	}),
	mockLicenseEndpoint({
		successResponse: HttpResponse.json(createLicense()),
	}),
	mockGetProcessInstanceEndpoint({
		successResponse: HttpResponse.json(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})),
	}),
	mockGetProcessInstanceWaitStateStatisticsEndpoint({
		successResponse: HttpResponse.json(createPaginatedResponse({items: []})),
	}),
];

export {PROCESS_INSTANCE_ID, processInstancePanelErrorBaseHandlers};
