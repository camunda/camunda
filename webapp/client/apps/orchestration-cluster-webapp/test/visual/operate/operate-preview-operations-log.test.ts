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

test('should match the operations log page snapshot', async ({page, operatePreviewOperationsLogPage}) => {
	await operatePreviewOperationsLogPage.goto();
	await expect(page.getByText('Invoice Decision')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the filters panel snapshot with populated filters', async ({
	page,
	operatePreviewOperationsLogPage,
}) => {
	await operatePreviewOperationsLogPage.goto('?actorId=demo&processInstanceKey=42');
	await expect(page.getByLabel('Actor', {exact: true})).toHaveValue('demo');

	await expect(operatePreviewOperationsLogPage.filtersPanel).toHaveScreenshot();
});

test('should match the empty state snapshot', async ({network, page, operatePreviewOperationsLogPage}) => {
	network.use(mockQueryAuditLogsEndpoint({successResponse: HttpResponse.json(createQueryAuditLogsResponse())}));
	await operatePreviewOperationsLogPage.goto();
	await expect(page.getByText('No operation log items yet')).toBeVisible();

	await expect(operatePreviewOperationsLogPage.table).toHaveScreenshot();
});
