/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {format, parseISO} from 'date-fns';
import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {createAuditLog} from '#/shared-test-modules/api-mocks/audit-logs';
import {OperationsLogDetailsModal} from './OperationsLogDetailsModal';

const OPERATE_ROOT_PATH = '/operate';
const BASEPATH_CASES = [
	{basepath: '', expectedPathPrefix: ''},
	{basepath: '/camunda', expectedPathPrefix: '/camunda'},
	{
		basepath: '/camunda/physical-tenants/tenant-a',
		expectedPathPrefix: '/camunda/physical-tenants/tenant-a',
	},
] as const;

function renderModal(auditLog: ReturnType<typeof createAuditLog>, basepath = '') {
	return renderWithRouter(() => <OperationsLogDetailsModal isOpen onClose={() => {}} auditLog={auditLog} />, {
		path: OPERATE_ROOT_PATH,
		basepath,
		initialEntry: `${basepath}${OPERATE_ROOT_PATH}`,
	});
}

describe('<OperationsLogDetailsModal />', () => {
	it('should render the status, actor, entity key and date rows', async () => {
		const auditLog = createAuditLog({
			operationType: 'CREATE',
			entityType: 'USER_TASK',
			result: 'SUCCESS',
			actorId: 'demo',
			actorType: 'USER',
			timestamp: '2024-01-01T10:00:00.000Z',
		});

		const screen = await renderModal(auditLog);

		await expect.element(screen.getByRole('dialog')).toBeVisible();
		await expect.element(screen.getByText('Status')).toBeVisible();
		await expect.element(screen.getByText('Actor')).toBeVisible();
		await expect.element(screen.getByText('demo').first()).toBeVisible();
		await expect.element(screen.getByText(format(parseISO(auditLog.timestamp), 'yyyy-MM-dd HH:mm:ss'))).toBeVisible();
	});

	it.for(BASEPATH_CASES)(
		'should show a link to the batch operation when the audit log is part of a batch at basepath "$basepath"',
		async ({basepath, expectedPathPrefix}) => {
			const auditLog = createAuditLog({
				entityType: 'USER_TASK',
				batchOperationKey: 'batch-123',
			});

			const screen = await renderModal(auditLog, basepath);

			await expect.element(screen.getByRole('dialog')).toMatchTextContent('This operation is part of a batch.');
			await expect
				.element(screen.getByRole('link', {name: 'View batch operation details.'}))
				.toHaveAttribute('href', `${expectedPathPrefix}/operate/batch-operations/batch-123`);
		},
	);

	it.for(BASEPATH_CASES)(
		'should render batch entity links in the details modal with the correct basepath "$basepath"',
		async ({basepath, expectedPathPrefix}) => {
			const auditLog = createAuditLog({
				entityType: 'BATCH',
				operationType: 'CANCEL',
				batchOperationKey: 'batch-456',
				batchOperationType: 'CANCEL_PROCESS_INSTANCE',
			});

			const screen = await renderModal(auditLog, basepath);

			await expect.element(screen.getByText('Applied to:')).toBeVisible();
			await expect.element(screen.getByText(/process instances/)).toBeVisible();
			await expect
				.element(screen.getByRole('link').filter({hasText: 'batch-456'}))
				.toHaveAttribute('href', `${expectedPathPrefix}/operate/batch-operations/batch-456`);
			await expect
				.element(screen.getByRole('link').filter({hasText: 'View batch operation details'}))
				.toHaveAttribute('href', `${expectedPathPrefix}/operate/batch-operations/batch-456`);
		},
	);

	it.for(BASEPATH_CASES)(
		'should preserve the legacy null batch key destination at basepath "$basepath"',
		async ({basepath, expectedPathPrefix}) => {
			const auditLog = createAuditLog({
				entityType: 'BATCH',
				batchOperationKey: null,
				batchOperationType: 'CANCEL_PROCESS_INSTANCE',
			});

			const screen = await renderModal(auditLog, basepath);

			await expect
				.element(screen.getByRole('link').filter({hasText: 'View batch operation details'}))
				.toHaveAttribute('href', `${expectedPathPrefix}/operate/batch-operations/null`);
		},
	);

	it.for(BASEPATH_CASES)(
		'should render a process instance entity link with the correct basepath "$basepath"',
		async ({basepath, expectedPathPrefix}) => {
			const auditLog = createAuditLog({
				entityType: 'PROCESS_INSTANCE',
				entityKey: '2251799813685250',
				processInstanceKey: '2251799813685250',
				processDefinitionId: 'order-process',
			});

			const screen = await renderModal(auditLog, basepath);

			await expect
				.element(screen.getByRole('link', {name: 'View process instance 2251799813685250'}))
				.toHaveAttribute('href', `${expectedPathPrefix}/operate/processes/2251799813685250`);
		},
	);

	it.for(BASEPATH_CASES)(
		'should render a parent process instance link with the correct basepath "$basepath"',
		async ({basepath, expectedPathPrefix}) => {
			const auditLog = createAuditLog({
				entityType: 'VARIABLE',
				entityKey: 'variable-1',
				processInstanceKey: '2251799813685250',
				processDefinitionId: 'order-process',
			});

			const screen = await renderModal(auditLog, basepath);

			await expect
				.element(screen.getByRole('link', {name: 'View process instance 2251799813685250'}))
				.toHaveAttribute('href', `${expectedPathPrefix}/operate/processes/2251799813685250`);
		},
	);

	it.for(BASEPATH_CASES)(
		'should render a decision instance entity link with the correct basepath "$basepath"',
		async ({basepath, expectedPathPrefix}) => {
			const auditLog = createAuditLog({
				entityType: 'DECISION',
				entityKey: '2251799813685250',
				operationType: 'EVALUATE',
				decisionDefinitionKey: '2251799813685250',
			});

			const screen = await renderModal(auditLog, basepath);

			await expect
				.element(screen.getByRole('link', {name: 'View decision instance 2251799813685250'}))
				.toHaveAttribute('href', `${expectedPathPrefix}/operate/decisions/2251799813685250`);
		},
	);

	it('should render the resource key detail row for RESOURCE entity types', async () => {
		const auditLog = createAuditLog({
			entityType: 'RESOURCE',
			resourceKey: 'resource-789',
		});

		const screen = await renderModal(auditLog);

		await expect.element(screen.getByText('Details:')).toBeVisible();
		await expect.element(screen.getByText('Resource key')).toBeVisible();
		await expect.element(screen.getByText('resource-789')).toBeVisible();
	});
});
