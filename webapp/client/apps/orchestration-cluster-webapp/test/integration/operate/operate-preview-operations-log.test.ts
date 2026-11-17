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

test('should render the audit logs with entity links', async ({page, operatePreviewOperationsLogPage}) => {
	await operatePreviewOperationsLogPage.goto();

	await expect(operatePreviewOperationsLogPage.heading).toBeAttached();
	await expect(page).toHaveTitle('Operate: Operations Log');
	await expect(operatePreviewOperationsLogPage.table).toBeVisible();
	await expect(page.getByText('Invoice Decision')).toBeVisible();
	await expect(page.getByRole('heading', {name: 'Operations Log - 2 results'})).toBeVisible();
	await expect(page.getByRole('link', {name: 'View process instance 42'}).first()).toBeVisible();
});

test('should filter by actor and keep the filter in the URL', async ({page, operatePreviewOperationsLogPage}) => {
	await operatePreviewOperationsLogPage.goto();
	await expect(operatePreviewOperationsLogPage.table).toBeVisible();

	await page.getByLabel('Actor', {exact: true}).fill('demo');

	await expect(page).toHaveURL(/actorId=demo/);
	await expect(page.getByRole('button', {name: 'Reset filters'})).toBeEnabled();
});

test('should keep audit rows and restore decision names on retry when the lookup fails', async ({
	network,
	page,
	operatePreviewOperationsLogPage,
}) => {
	network.use(mockQueryDecisionDefinitionsEndpoint({successResponse: new HttpResponse(null, {status: 503})}));

	await operatePreviewOperationsLogPage.goto();

	await expect(operatePreviewOperationsLogPage.decisionNamesError).toBeVisible();
	await expect(page.getByRole('link', {name: 'View decision instance 888'})).toBeVisible();

	network.use(
		mockQueryDecisionDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryDecisionDefinitionsResponse({
					items: [createDecisionDefinition({decisionDefinitionKey: '888', name: 'Invoice Decision'})],
				}),
			),
		}),
	);
	await operatePreviewOperationsLogPage.retryButton.click();

	await expect(page.getByText('Invoice Decision')).toBeVisible();
	await expect(operatePreviewOperationsLogPage.decisionNamesError).not.toBeVisible();
});

test('should keep keyboard focus on a row control across the background refresh', async ({
	network,
	page,
	operatePreviewOperationsLogPage,
}) => {
	await operatePreviewOperationsLogPage.goto();
	await expect(operatePreviewOperationsLogPage.table).toBeVisible();
	const openDetails = page.getByRole('button', {name: 'Open details'}).first();
	await openDetails.focus();
	await expect(openDetails).toBeFocused();

	network.use(
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
							auditLogKey: 'new-log',
							entityType: 'PROCESS_INSTANCE',
							entityKey: '77',
							processInstanceKey: '77',
						}),
					],
				}),
			),
		}),
	);
	await expect(page.getByRole('link', {name: 'View process instance 77'}).first()).toBeVisible({timeout: 10_000});

	await expect(openDetails).toBeFocused();
});

test('should keep loading pages until the last one while scrolling down', async ({
	page,
	operatePreviewOperationsLogPage,
}) => {
	const totalItems = 260;
	await page.route('**/v2/audit-logs/search', async (route) => {
		const from: number = route.request().postDataJSON().page?.from ?? 0;
		const items = Array.from({length: Math.min(50, totalItems - from)}, (_, index) =>
			createAuditLog({
				auditLogKey: `log-${from + index}`,
				entityType: 'PROCESS_INSTANCE',
				entityKey: `${from + index}`,
				processInstanceKey: `${from + index}`,
			}),
		);
		await route.fulfill({json: createQueryAuditLogsResponse({items, page: {totalItems}})});
	});
	await operatePreviewOperationsLogPage.goto();
	await expect(operatePreviewOperationsLogPage.table).toBeVisible();

	await page.mouse.move(600, 500);
	await expect(async () => {
		await page.mouse.wheel(0, 600);
		await expect(page.getByRole('link', {name: 'View process instance 259'}).first()).toBeVisible({timeout: 200});
	}).toPass({timeout: 25_000, intervals: [100]});
});
