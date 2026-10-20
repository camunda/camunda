/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {z} from 'zod';
import {test, expect} from '#/pw-modules/test-extend';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createAuditLog, createQueryAuditLogsResponse} from '#/shared-test-modules/api-mocks/audit-logs';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';
import {mockQueryAuditLogsEndpoint} from '#/shared-test-modules/mock-handlers';

function mockInstanceAuditLogs(processInstanceKey: string) {
	return mockQueryAuditLogsEndpoint({
		schema: z.object({
			filter: z.object({
				category: z.object({$neq: z.literal('ADMIN')}),
				processInstanceKey: z.literal(processInstanceKey),
				operationType: z.object({$in: z.tuple([z.literal('CANCEL')])}),
			}),
		}),
		successResponse: HttpResponse.json(
			createQueryAuditLogsResponse({
				items: [createAuditLog({operationType: 'CANCEL', entityType: 'PROCESS_INSTANCE', processInstanceKey})],
			}),
		),
		failureResponse: HttpResponse.json(createQueryAuditLogsResponse()),
	});
}

test('should show the operations log on direct entry with the filter from the URL', async ({
	network,
	page,
	operateProcessInstancePage,
}) => {
	const instance = createProcessInstance({processInstanceKey: '2251799813856041', tenantId: 'tenant-a'});
	network.use(...processInstanceHeaderHandlers(instance));
	network.use(mockInstanceAuditLogs(instance.processInstanceKey));

	await operateProcessInstancePage.goto(
		instance.processInstanceKey,
		'/operations-log?tenantId=tenant-a&operationType=CANCEL',
	);

	await expect(page.getByRole('link', {name: 'Operations Log', exact: true})).toHaveAttribute('aria-current', 'page');
	await expect(page.getByRole('cell', {name: 'Cancel', exact: true})).toBeVisible();
	await expect(page.getByRole('cell', {name: 'Process instance', exact: true})).toBeVisible();
	await expect(page).toHaveURL(/tenantId=tenant-a/);
});

test('should open the operations log from the tabs and keep the URL search', async ({
	network,
	page,
	operateProcessInstancePage,
}) => {
	const instance = createProcessInstance({processInstanceKey: '2251799813856042', tenantId: 'tenant-a'});
	network.use(...processInstanceHeaderHandlers(instance));
	network.use(mockInstanceAuditLogs(instance.processInstanceKey));

	await operateProcessInstancePage.goto(
		instance.processInstanceKey,
		'/variables?tenantId=tenant-a&operationType=CANCEL',
	);
	await page.getByRole('link', {name: 'Operations Log', exact: true}).click();

	await expect(page).toHaveURL(
		new RegExp(`/operate/processes/${instance.processInstanceKey}/operations-log\\?.*tenantId=tenant-a`),
	);
	await expect(page.getByRole('cell', {name: 'Cancel', exact: true})).toBeVisible();
});
