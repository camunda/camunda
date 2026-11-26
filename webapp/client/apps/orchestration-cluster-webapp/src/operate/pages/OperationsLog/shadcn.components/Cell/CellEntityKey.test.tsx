/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {createAuditLog} from '#/shared-test-modules/api-mocks/audit-logs';
import {CellEntityKey} from './CellEntityKey';

function renderEntityKey(item: ReturnType<typeof createAuditLog>, tenantId?: string) {
	return renderWithRouter(() => <CellEntityKey item={item} tenantId={tenantId} />, {
		path: '/operate-preview/operations-log',
		initialEntry: '/operate-preview/operations-log',
	});
}

describe('<CellEntityKey /> (design system)', () => {
	it.for([
		{
			entityType: 'USER_TASK',
			operationType: 'ASSIGN',
			entityKey: '2251799813685279',
			batchOperationKey: null,
			linkName: 'View user task 2251799813685279',
			href: '/tasklist/2251799813685279',
		},
		{
			entityType: 'PROCESS_INSTANCE',
			operationType: 'MODIFY',
			entityKey: '2251799813685280',
			batchOperationKey: null,
			linkName: 'View process instance 2251799813685280',
			href: '/operate-preview/processes/2251799813685280',
		},
		{
			entityType: 'DECISION',
			operationType: 'EVALUATE',
			entityKey: '2251799813685281',
			batchOperationKey: null,
			linkName: 'View decision instance 2251799813685281',
			href: '/operate/decisions/2251799813685281',
		},
		{
			entityType: 'BATCH',
			operationType: 'CREATE',
			entityKey: '2251799813685282',
			batchOperationKey: '2251799813685282',
			linkName: 'View batch operation 2251799813685282',
			href: '/operate-preview/batch-operations/2251799813685282',
		},
	] as const)('should keep the $entityType entity link inside the design system preview', async (testCase) => {
		const screen = await renderEntityKey(
			createAuditLog({
				entityType: testCase.entityType,
				operationType: testCase.operationType,
				entityKey: testCase.entityKey,
				batchOperationKey: testCase.batchOperationKey,
			}),
		);

		await expect.element(screen.getByRole('link', {name: testCase.linkName})).toHaveAttribute('href', testCase.href);
	});

	it('should link to the process instance within its tenant', async () => {
		const screen = await renderEntityKey(
			createAuditLog({entityType: 'PROCESS_INSTANCE', operationType: 'MODIFY', entityKey: '2251799813685280'}),
			'tenant-a',
		);

		await expect
			.element(screen.getByRole('link', {name: 'View process instance 2251799813685280'}))
			.toHaveAttribute('href', '/operate-preview/processes/2251799813685280?tenantId=tenant-a');
	});
});
