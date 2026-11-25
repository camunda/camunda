/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {
	mockC3ClustersEndpoint,
	mockC3NotificationEventsEndpoint,
	mockC3NotificationsEndpoint,
	mockC3OrganizationsEndpoint,
	mockQueryGroupsEndpoint,
	mockQueryMappingRulesEndpoint,
	mockSaasTokenEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createC3Cluster, createC3Organization, createSaasUserToken} from '#/shared-test-modules/api-mocks/saas';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createQueryGroupsResponse} from '#/shared-test-modules/api-mocks/groups';
import {createQueryMappingRulesResponse} from '#/shared-test-modules/api-mocks/mapping-rules';

function createSaasAdminHandlers() {
	return [
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(
				createSystemConfiguration({
					components: {active: ['admin']},
					authentication: {canLogout: true, isLoginDelegated: true, isCamundaGroupsEnabled: true},
				}),
			),
		}),
		mockSaasTokenEndpoint({successResponse: HttpResponse.json(createSaasUserToken())}),
		mockC3OrganizationsEndpoint({successResponse: HttpResponse.json([createC3Organization()])}),
		mockC3ClustersEndpoint({successResponse: HttpResponse.json([createC3Cluster()])}),
		mockC3NotificationsEndpoint({successResponse: HttpResponse.json([])}),
		mockC3NotificationEventsEndpoint({
			successResponse: new HttpResponse('', {headers: {'Content-Type': 'text/event-stream'}}),
		}),
		mockQueryGroupsEndpoint({successResponse: HttpResponse.json(createQueryGroupsResponse())}),
		mockQueryMappingRulesEndpoint({successResponse: HttpResponse.json(createQueryMappingRulesResponse())}),
	];
}

export {createSaasAdminHandlers};
