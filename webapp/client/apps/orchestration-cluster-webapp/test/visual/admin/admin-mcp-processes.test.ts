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
	mockQueryMessageSubscriptionsEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createMessageSubscription,
	createQueryMessageSubscriptionsResponse,
} from '#/shared-test-modules/api-mocks/message-subscriptions';

const TOOLS = [
	createMessageSubscription({messageSubscriptionKey: '1', toolName: 'place-order', tenantId: 'acme'}),
	createMessageSubscription({
		messageSubscriptionKey: '2',
		toolName: 'cancel-order',
		processDefinitionName: null,
		processDefinitionId: 'cancellation-process',
		processDefinitionVersion: null,
		tenantId: 'acme',
		toolProperties: {},
	}),
];

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(
				createSystemConfiguration({
					components: {active: ['admin']},
					deployment: {isMultiTenancyEnabled: false, isTenantsApiEnabled: true, maxRequestSize: 0},
				}),
			),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryMessageSubscriptionsEndpoint({
			successResponse: HttpResponse.json(createQueryMessageSubscriptionsResponse({items: TOOLS})),
		}),
	);
});

test('should match the MCP processes page snapshot', async ({adminMcpProcessesPage, page}) => {
	await adminMcpProcessesPage.goto();
	await expect(adminMcpProcessesPage.cell('place-order')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the expanded tool snapshot', async ({adminMcpProcessesPage, page}) => {
	await adminMcpProcessesPage.goto();
	await adminMcpProcessesPage.expandToggle('place-order').click();
	await expect(adminMcpProcessesPage.detailHeading('Purpose')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the empty MCP processes page snapshot', async ({adminMcpProcessesPage, page, network}) => {
	network.use(
		mockQueryMessageSubscriptionsEndpoint({
			successResponse: HttpResponse.json(createQueryMessageSubscriptionsResponse({items: []})),
		}),
	);

	await adminMcpProcessesPage.goto();
	await expect(adminMcpProcessesPage.detailText('No MCP processes found.')).toBeVisible();

	await expect(page).toHaveScreenshot();
});
