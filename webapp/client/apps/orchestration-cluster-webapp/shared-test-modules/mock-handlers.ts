/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {endpoints} from '@camunda/camunda-api-zod-schemas/8.10';
import {createEndpointMock} from './mock-endpoint';

const mockGetProcessDefinitionInstanceStatisticsEndpoint = createEndpointMock({
	endpoint: endpoints.getProcessDefinitionInstanceStatistics.getUrl(),
	method: endpoints.getProcessDefinitionInstanceStatistics.method,
});

const mockGetIncidentProcessInstanceStatisticsByErrorEndpoint = createEndpointMock({
	endpoint: endpoints.getIncidentProcessInstanceStatisticsByError.getUrl(),
	method: endpoints.getIncidentProcessInstanceStatisticsByError.method,
});

const mockCurrentUserEndpoint = createEndpointMock({
	endpoint: endpoints.getCurrentUser.getUrl(),
	method: endpoints.getCurrentUser.method,
});

const mockLoginEndpoint = createEndpointMock({
	endpoint: '/login',
	method: 'POST',
});

const mockLogoutEndpoint = createEndpointMock({
	endpoint: '/logout',
	method: 'POST',
});

const mockSystemConfigurationEndpoint = createEndpointMock({
	endpoint: endpoints.getSystemConfiguration.getUrl(),
	method: endpoints.getSystemConfiguration.method,
});

const mockLicenseEndpoint = createEndpointMock({
	endpoint: endpoints.getLicense.getUrl(),
	method: endpoints.getLicense.method,
});

const mockSaasTokenEndpoint = createEndpointMock({
	endpoint: '/v2/authentication/me/token',
	method: 'GET',
});

export {
	mockCurrentUserEndpoint,
	mockLoginEndpoint,
	mockLogoutEndpoint,
	mockSystemConfigurationEndpoint,
	mockLicenseEndpoint,
	mockSaasTokenEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
};
