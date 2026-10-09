/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {z} from 'zod';
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

// The mock rejects a request that does not match, so the rows only render when the route
// asked for exactly the first page of undeleted start events that carry a tool.
const EXPECTED_INITIAL_REQUEST = z.object({
	sort: z.tuple([z.object({field: z.literal('toolName'), order: z.literal('asc')})]),
	filter: z.object({
		messageSubscriptionType: z.literal('START_EVENT'),
		messageSubscriptionState: z.object({$neq: z.literal('DELETED')}),
		toolName: z.object({$exists: z.literal(true)}),
	}),
	page: z.object({from: z.literal(0), limit: z.literal(20)}),
});

const TOOLS = [
	createMessageSubscription({
		messageSubscriptionKey: '1',
		toolName: 'place-order',
		processDefinitionName: 'Order process',
		processDefinitionVersion: 3,
		tenantId: 'acme',
	}),
	createMessageSubscription({
		messageSubscriptionKey: '2',
		toolName: 'cancel-order',
		processDefinitionName: null,
		processDefinitionId: 'cancellation-process',
		processDefinitionVersion: null,
		tenantId: 'acme',
		toolProperties: {'io.camunda.tool:purpose': 'Cancels a placed order'},
	}),
];

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser()),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['admin']}})),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
		mockQueryMessageSubscriptionsEndpoint({
			successResponse: HttpResponse.json(createQueryMessageSubscriptionsResponse({items: TOOLS})),
		}),
	);
});

test.describe('Admin MCP processes', () => {
	test('should list the tools the cluster exposes', async ({adminMcpProcessesPage}) => {
		await adminMcpProcessesPage.goto();

		await expect(adminMcpProcessesPage.heading).toBeVisible();
		await expect(adminMcpProcessesPage.cell('place-order')).toBeVisible();
		await expect(adminMcpProcessesPage.cell('Places a customer order')).toBeVisible();
		await expect(adminMcpProcessesPage.cell('Order process')).toBeVisible();
		await expect(adminMcpProcessesPage.cell('3')).toBeVisible();
	});

	test('should name a process by its id when it has no name, and dash its missing version', async ({
		adminMcpProcessesPage,
	}) => {
		await adminMcpProcessesPage.goto();

		const row = adminMcpProcessesPage.row('cancel-order');

		await expect(row).toContainText('cancellation-process');
		await expect(row).toContainText('-');
	});

	test('should ask the API only for start events that expose a tool', async ({adminMcpProcessesPage, network}) => {
		network.use(
			mockQueryMessageSubscriptionsEndpoint({
				schema: EXPECTED_INITIAL_REQUEST,
				successResponse: HttpResponse.json(createQueryMessageSubscriptionsResponse({items: TOOLS})),
				failureResponse: HttpResponse.json({}, {status: 400}),
			}),
		);

		await adminMcpProcessesPage.goto();

		await expect(adminMcpProcessesPage.cell('place-order')).toBeVisible();
	});

	test('should hide the tenant column until the tenants API is enabled', async ({adminMcpProcessesPage, network}) => {
		await adminMcpProcessesPage.goto();

		await expect(adminMcpProcessesPage.cell('place-order')).toBeVisible();
		await expect(adminMcpProcessesPage.columnHeader('Tenant')).toBeHidden();

		network.use(
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(
					createSystemConfiguration({
						components: {active: ['admin']},
						deployment: {isMultiTenancyEnabled: false, isTenantsApiEnabled: true, maxRequestSize: 0},
					}),
				),
			}),
		);
		await adminMcpProcessesPage.goto();

		await expect(adminMcpProcessesPage.columnHeader('Tenant')).toBeVisible();
		await expect(adminMcpProcessesPage.cell('acme').first()).toBeVisible();
	});

	test('should reveal the tool documentation when a row is expanded', async ({adminMcpProcessesPage}) => {
		await adminMcpProcessesPage.goto();

		await adminMcpProcessesPage.expandToggle('place-order').click();

		await expect(adminMcpProcessesPage.detailHeading('Purpose')).toBeVisible();
		await expect(adminMcpProcessesPage.detailText('When the customer has confirmed their cart')).toBeVisible();
	});

	test('should say so where the tool documents nothing', async ({adminMcpProcessesPage}) => {
		await adminMcpProcessesPage.goto();

		await adminMcpProcessesPage.expandToggle('cancel-order').click();

		await expect(adminMcpProcessesPage.detailText('No information provided.').first()).toBeVisible();
	});

	test('should filter the list by tool name', async ({adminMcpProcessesPage, page, network}) => {
		await adminMcpProcessesPage.goto();
		await expect(adminMcpProcessesPage.cell('place-order')).toBeVisible();

		network.use(
			mockQueryMessageSubscriptionsEndpoint({
				successResponse: HttpResponse.json(createQueryMessageSubscriptionsResponse({items: [TOOLS[1]!]})),
			}),
		);
		await adminMcpProcessesPage.searchField.fill('cancel');

		await expect(page).toHaveURL(/search=cancel/);
		await expect(adminMcpProcessesPage.cell('cancel-order')).toBeVisible();
		await expect(adminMcpProcessesPage.cell('place-order')).toBeHidden();
	});

	test('should keep the filter in the URL so the view can be shared', async ({adminMcpProcessesPage, page}) => {
		await page.goto('/admin/mcp-processes?search=cancel');

		await expect(adminMcpProcessesPage.searchField).toHaveValue('cancel');
	});

	test('should reverse the tool name order when the column is sorted', async ({adminMcpProcessesPage, page}) => {
		await adminMcpProcessesPage.goto();
		await expect(adminMcpProcessesPage.cell('place-order')).toBeVisible();

		await adminMcpProcessesPage.toolNameSortButton.click();

		await expect(page).toHaveURL(/sortOrder=desc/);
	});

	test('should report a load failure instead of an empty list', async ({adminMcpProcessesPage, network}) => {
		network.use(
			mockQueryMessageSubscriptionsEndpoint({
				successResponse: HttpResponse.json(createQueryMessageSubscriptionsResponse(), {status: 500}),
			}),
		);

		await adminMcpProcessesPage.goto();

		await expect(adminMcpProcessesPage.loadFailureHeading).toBeVisible();
		await expect(adminMcpProcessesPage.table).toBeHidden();
	});
});
