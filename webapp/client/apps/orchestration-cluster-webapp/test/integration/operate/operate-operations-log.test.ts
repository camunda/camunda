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
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockLicenseEndpoint,
	mockQueryAuditLogsEndpoint,
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createQueryProcessDefinitionsResponse} from '#/shared-test-modules/api-mocks/process-definitions';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {createAuditLog, createQueryAuditLogsResponse} from '#/shared-test-modules/api-mocks/audit-logs';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockGetProcessDefinitionInstanceStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
		}),
		mockQueryAuditLogsEndpoint({
			successResponse: HttpResponse.json(
				createQueryAuditLogsResponse({
					items: [
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

test('should show a direct-entry audit log when decision names fail and restore names on retry', async ({
	network,
	page,
	operateOperationsLogPage,
	makeAxeBuilder,
}) => {
	network.use(mockQueryDecisionDefinitionsEndpoint({successResponse: new HttpResponse(null, {status: 503})}));

	await operateOperationsLogPage.goto();

	await expect(operateOperationsLogPage.table).toBeVisible();
	await expect(operateOperationsLogPage.decisionNamesError).toBeVisible();
	await expect(page.getByRole('heading', {name: 'Operations Log - 1 result'})).toBeVisible();
	await expect(page.getByRole('link', {name: 'View decision instance 888'})).toHaveAttribute(
		'href',
		'/operate/decisions/888',
	);
	await expect(page.getByText("Couldn't fetch audit logs")).not.toBeVisible();
	expect((await makeAxeBuilder().include('[role="alert"]').analyze()).violations).toEqual([]);

	network.use(
		mockQueryDecisionDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryDecisionDefinitionsResponse({
					items: [createDecisionDefinition({decisionDefinitionKey: '888', name: 'Invoice Decision'})],
				}),
			),
		}),
	);
	await operateOperationsLogPage.retryButton.click();

	await expect(page.getByText('Invoice Decision')).toBeVisible();
	await expect(operateOperationsLogPage.decisionNamesError).not.toBeVisible();
	await expect(operateOperationsLogPage.table).toBeVisible();
});
