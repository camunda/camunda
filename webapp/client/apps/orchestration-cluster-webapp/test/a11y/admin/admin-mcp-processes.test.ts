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

// TEMPORARY: the design system's DataTable injects the expand-toggle column with
// `header: () => null`, so its `<th>` has no discernible text and axe flags every table
// that uses `expansion`. There is no prop to name that column, so this cannot be fixed
// here — remove the exclusion once the design system names it. Every other rule, and
// every other element, is still scanned.
const DS_EXPAND_COLUMN_HEADER_RULE = 'empty-table-header';

const TOOLS = [
	createMessageSubscription({messageSubscriptionKey: '1', toolName: 'place-order'}),
	createMessageSubscription({
		messageSubscriptionKey: '2',
		toolName: 'cancel-order',
		processDefinitionName: null,
		processDefinitionId: 'cancellation-process',
		processDefinitionVersion: null,
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

test('should have no accessibility violations in the populated MCP processes page', async ({
	adminMcpProcessesPage,
	makeAxeBuilder,
}) => {
	await adminMcpProcessesPage.goto();
	await expect(adminMcpProcessesPage.cell('place-order')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_EXPAND_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations with a tool expanded', async ({
	adminMcpProcessesPage,
	makeAxeBuilder,
}) => {
	await adminMcpProcessesPage.goto();
	await adminMcpProcessesPage.expandToggle('place-order').click();
	await expect(adminMcpProcessesPage.detailHeading('Purpose')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_EXPAND_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the empty state', async ({
	adminMcpProcessesPage,
	makeAxeBuilder,
	network,
}) => {
	network.use(
		mockQueryMessageSubscriptionsEndpoint({
			successResponse: HttpResponse.json(createQueryMessageSubscriptionsResponse({items: []})),
		}),
	);

	await adminMcpProcessesPage.goto();
	await expect(adminMcpProcessesPage.detailText('No MCP processes found.')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_EXPAND_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});
