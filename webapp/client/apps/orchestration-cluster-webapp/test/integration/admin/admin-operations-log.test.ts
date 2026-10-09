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
	mockQueryAuditLogsEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createAuditLog, createQueryAuditLogsResponse} from '#/shared-test-modules/api-mocks/audit-logs';

// The mock rejects a request that does not match, so the rows only render when the route
// asked for exactly the first page of admin-category audit logs, sorted by timestamp desc.
const EXPECTED_INITIAL_REQUEST = z.object({
	sort: z.tuple([z.object({field: z.literal('timestamp'), order: z.literal('desc')})]),
	filter: z.object({category: z.object({$eq: z.literal('ADMIN')})}),
	page: z.object({from: z.literal(0), limit: z.literal(50)}),
});

const LOGS = [
	createAuditLog({
		auditLogKey: '1',
		entityType: 'ROLE',
		operationType: 'CREATE',
		actorId: 'demo',
		actorType: 'USER',
		result: 'SUCCESS',
		timestamp: '2026-01-02T10:00:00.000Z',
	}),
	createAuditLog({
		auditLogKey: '2',
		entityType: 'AUTHORIZATION',
		operationType: 'CREATE',
		actorId: 'service-account',
		actorType: 'CLIENT',
		result: 'FAIL',
		entityDescription: 'ERR_VALIDATION',
		relatedEntityType: 'USER',
		relatedEntityKey: 'owner-key',
		timestamp: '2026-01-01T10:00:00.000Z',
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
		mockQueryAuditLogsEndpoint({
			successResponse: HttpResponse.json(createQueryAuditLogsResponse({items: LOGS})),
		}),
	);
});

test.describe('Admin operations log', () => {
	test('should list the audit log entries', async ({adminOperationsLogPage}) => {
		await adminOperationsLogPage.goto();

		await expect(adminOperationsLogPage.heading).toBeVisible();
		await expect(adminOperationsLogPage.cell('demo')).toBeVisible();
		await expect(adminOperationsLogPage.cell('service-account')).toBeVisible();
	});

	test('should ask the API only for the admin-category audit logs on the first page', async ({
		adminOperationsLogPage,
		network,
	}) => {
		network.use(
			mockQueryAuditLogsEndpoint({
				schema: EXPECTED_INITIAL_REQUEST,
				successResponse: HttpResponse.json(createQueryAuditLogsResponse({items: LOGS})),
				failureResponse: HttpResponse.json({}, {status: 400}),
			}),
		);

		await adminOperationsLogPage.goto();

		await expect(adminOperationsLogPage.cell('demo')).toBeVisible();
	});

	test('should filter by operation type', async ({adminOperationsLogPage, page, network}) => {
		await adminOperationsLogPage.goto();
		await expect(adminOperationsLogPage.cell('demo')).toBeVisible();

		network.use(
			mockQueryAuditLogsEndpoint({
				schema: EXPECTED_INITIAL_REQUEST.extend({
					filter: z.object({
						category: z.object({$eq: z.literal('ADMIN')}),
						operationType: z.literal('CREATE'),
					}),
				}),
				successResponse: HttpResponse.json(createQueryAuditLogsResponse({items: LOGS})),
				failureResponse: HttpResponse.json({}, {status: 400}),
			}),
		);

		await adminOperationsLogPage.operationTypeFilter.click();
		await page.getByRole('option', {name: 'Create'}).click();

		await expect(page).toHaveURL(/operationType=CREATE/);
	});

	test('should reveal the owner filters only for the AUTHORIZATION entity type, and send them in the request', async ({
		adminOperationsLogPage,
		page,
		network,
	}) => {
		await adminOperationsLogPage.goto();
		await expect(adminOperationsLogPage.ownerKeyFilter).toBeHidden();

		await adminOperationsLogPage.entityTypeFilter.click();
		await page.getByRole('option', {name: 'Authorization'}).click();
		await expect(adminOperationsLogPage.ownerKeyFilter).toBeVisible();

		network.use(
			mockQueryAuditLogsEndpoint({
				schema: EXPECTED_INITIAL_REQUEST.extend({
					filter: z.object({
						category: z.object({$eq: z.literal('ADMIN')}),
						entityType: z.literal('AUTHORIZATION'),
						relatedEntityKey: z.literal('owner-key'),
					}),
				}),
				successResponse: HttpResponse.json(createQueryAuditLogsResponse({items: LOGS})),
				failureResponse: HttpResponse.json({}, {status: 400}),
			}),
		);

		await adminOperationsLogPage.ownerKeyFilter.fill('owner-key');

		await expect(page).toHaveURL(/relatedEntityKey=owner-key/);
	});

	test('should filter by actor once the reader stops typing', async ({adminOperationsLogPage, page, network}) => {
		await adminOperationsLogPage.goto();
		await expect(adminOperationsLogPage.cell('demo')).toBeVisible();

		network.use(
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(createQueryAuditLogsResponse({items: [LOGS[0]!]})),
			}),
		);
		await adminOperationsLogPage.actorFilter.fill('demo');

		await expect(page).toHaveURL(/actor=demo/);
	});

	test('should keep the filters in the URL so the view can be shared', async ({adminOperationsLogPage, page}) => {
		await page.goto('/admin/operations-log?actor=demo&operationType=CREATE');

		await expect(adminOperationsLogPage.actorFilter).toHaveValue('demo');
	});

	test('should reverse the sort order when a column is sorted', async ({adminOperationsLogPage, page}) => {
		await adminOperationsLogPage.goto();
		await expect(adminOperationsLogPage.cell('demo')).toBeVisible();

		await adminOperationsLogPage.sortButton('Actor').click();

		await expect(page).toHaveURL(/sortField=actorId/);
	});

	test('should disable the reset control until a filter is active, then clear the filters on click', async ({
		adminOperationsLogPage,
		page,
	}) => {
		await page.goto('/admin/operations-log?actor=demo');

		await expect(adminOperationsLogPage.resetButton).toBeEnabled();

		await adminOperationsLogPage.resetButton.click();

		await expect(page).not.toHaveURL(/actor=demo/);
		await expect(adminOperationsLogPage.resetButton).toBeDisabled();
	});

	test('should report a load failure instead of an empty list', async ({adminOperationsLogPage, network}) => {
		network.use(
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(createQueryAuditLogsResponse(), {status: 500}),
			}),
		);

		await adminOperationsLogPage.goto();

		await expect(adminOperationsLogPage.loadFailureHeading).toBeVisible();
		await expect(adminOperationsLogPage.table).toBeHidden();
	});
});
