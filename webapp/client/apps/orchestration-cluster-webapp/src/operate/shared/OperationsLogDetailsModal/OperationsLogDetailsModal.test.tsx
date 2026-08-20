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

function renderModal(auditLog: ReturnType<typeof createAuditLog>) {
	return renderWithRouter(() => <OperationsLogDetailsModal isOpen onClose={() => {}} auditLog={auditLog} />, {
		path: '/operate',
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

	it('should show a link to the batch operation when the audit log is part of a batch', async () => {
		const auditLog = createAuditLog({
			entityType: 'USER_TASK',
			batchOperationKey: 'batch-123',
		});

		const screen = await renderModal(auditLog);

		await expect.element(screen.getByText('This operation is part of a batch.')).toBeVisible();
		await expect
			.element(screen.getByRole('link', {name: 'View batch operation details.'}))
			.toHaveAttribute('href', '/operate/batch-operations/batch-123');
	});

	it('should render the applied-to section for BATCH entity types', async () => {
		const auditLog = createAuditLog({
			entityType: 'BATCH',
			operationType: 'CANCEL',
			batchOperationKey: 'batch-456',
			batchOperationType: 'CANCEL_PROCESS_INSTANCE',
		});

		const screen = await renderModal(auditLog);

		await expect.element(screen.getByText('Applied to:')).toBeVisible();
		await expect.element(screen.getByText(/process instances/)).toBeVisible();
	});

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
