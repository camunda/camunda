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
	mockQueryAuditLogsEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createAuditLog, createQueryAuditLogsResponse} from '#/shared-test-modules/api-mocks/audit-logs';

const LOGS = [
	createAuditLog({auditLogKey: '1', actorId: 'demo', actorType: 'USER', result: 'SUCCESS'}),
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
	}),
];

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['admin']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryAuditLogsEndpoint({
			successResponse: HttpResponse.json(createQueryAuditLogsResponse({items: LOGS})),
		}),
	);
});

test('should match the operations log page snapshot', async ({adminOperationsLogPage, page}) => {
	await adminOperationsLogPage.goto();
	await expect(adminOperationsLogPage.cell('demo')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the operations log page snapshot with the AUTHORIZATION owner filters revealed', async ({
	adminOperationsLogPage,
	page,
}) => {
	await adminOperationsLogPage.goto();
	await adminOperationsLogPage.entityTypeFilter.click();
	await page.getByRole('option', {name: 'Authorization'}).click();
	await expect(adminOperationsLogPage.ownerKeyFilter).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the empty operations log page snapshot', async ({adminOperationsLogPage, page, network}) => {
	network.use(
		mockQueryAuditLogsEndpoint({
			successResponse: HttpResponse.json(createQueryAuditLogsResponse({items: []})),
		}),
	);

	await adminOperationsLogPage.goto();
	await expect(adminOperationsLogPage.heading).toBeVisible();

	await expect(page).toHaveScreenshot();
});
