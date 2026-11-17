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
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {createAuditLog, createQueryAuditLogsResponse} from '#/shared-test-modules/api-mocks/audit-logs';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['operate']})),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [createProcessDefinition({name: 'Order Process', processDefinitionId: 'order-process', version: 1})],
				}),
			),
		}),
		mockQueryDecisionDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryDecisionDefinitionsResponse({
					items: [createDecisionDefinition({decisionDefinitionKey: '888', name: 'Invoice Decision'})],
				}),
			),
		}),
		mockQueryAuditLogsEndpoint({
			successResponse: HttpResponse.json(
				createQueryAuditLogsResponse({
					items: [
						createAuditLog({
							auditLogKey: '1',
							entityType: 'PROCESS_INSTANCE',
							entityKey: '42',
							processInstanceKey: '42',
						}),
						createAuditLog({
							auditLogKey: 'decision-log',
							entityKey: '888',
							entityType: 'DECISION',
							operationType: 'EVALUATE',
							decisionDefinitionKey: '888',
						}),
					],
				}),
			),
		}),
	);
});

test('should have no accessibility violations with the filters and the audit log table', async ({
	operatePreviewOperationsLogPage,
	makeAxeBuilder,
}) => {
	await operatePreviewOperationsLogPage.goto();
	await expect(operatePreviewOperationsLogPage.table).toBeVisible();
	await expect(operatePreviewOperationsLogPage.filtersPanel).toBeVisible();

	const results = await makeAxeBuilder().analyze();
	expect(results.violations).toEqual([]);
});

test('should have no accessibility violations with the details modal open', async ({
	page,
	operatePreviewOperationsLogPage,
	makeAxeBuilder,
}) => {
	await operatePreviewOperationsLogPage.goto();
	await page.getByRole('button', {name: 'Open details'}).first().click();
	await expect(page.getByRole('dialog')).toBeVisible();

	const results = await makeAxeBuilder().analyze();
	expect(results.violations).toEqual([]);
});

test('should have no accessibility violations when the audit log request fails', async ({
	network,
	operatePreviewOperationsLogPage,
	makeAxeBuilder,
}) => {
	network.use(mockQueryAuditLogsEndpoint({successResponse: new HttpResponse(null, {status: 500})}));
	await operatePreviewOperationsLogPage.goto();
	await expect(operatePreviewOperationsLogPage.table.getByText("Couldn't fetch audit logs")).toBeVisible({
		timeout: 20_000,
	});

	const results = await makeAxeBuilder().analyze();
	expect(results.violations).toEqual([]);
});
