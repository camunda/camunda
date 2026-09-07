/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {createAuditLog} from '#/shared-test-modules/api-mocks/audit-logs';
import {it} from '#/vitest-modules/test-extend';
import {HistoryItemDetailsModal} from './HistoryItemDetailsModal';

describe('<HistoryItemDetailsModal />', () => {
	it('should show the history item details', async () => {
		const screen = await render(
			<HistoryItemDetailsModal
				auditLog={createAuditLog({operationType: 'CREATE', actorId: 'alice', timestamp: '2024-01-01T10:00:00.000Z'})}
				onClose={vi.fn()}
			/>,
		);

		await expect.element(screen.getByRole('heading', {name: 'Create task'})).toBeVisible();
		await expect.element(screen.getByText('Actor')).toBeVisible();
		await expect.element(screen.getByText('alice')).toBeVisible();
		await expect.element(screen.getByText('Time')).toBeVisible();
	});

	it('should show assignment details for assignment entries', async () => {
		const screen = await render(
			<HistoryItemDetailsModal
				auditLog={createAuditLog({operationType: 'ASSIGN', actorId: 'jane', relatedEntityKey: 'john'})}
				onClose={vi.fn()}
			/>,
		);

		await expect.element(screen.getByRole('heading', {name: 'Assign task'})).toBeVisible();
		await expect.element(screen.getByText('Details:')).toBeVisible();
		await expect.element(screen.getByText('Assignee')).toBeVisible();
		await expect.element(screen.getByText('john')).toBeVisible();
	});

	it('should hide assignment details for non-assignment entries', async () => {
		const screen = await render(
			<HistoryItemDetailsModal auditLog={createAuditLog({operationType: 'COMPLETE'})} onClose={vi.fn()} />,
		);

		await expect.element(screen.getByRole('heading', {name: 'Complete task'})).toBeVisible();
		await expect.element(screen.getByText('Assignee')).not.toBeInTheDocument();
	});

	it('should close when the user clicks the close button', async () => {
		const onClose = vi.fn();
		const screen = await render(<HistoryItemDetailsModal auditLog={createAuditLog()} onClose={onClose} />);

		await userEvent.click(screen.getByRole('button', {name: 'Close'}));

		expect(onClose).toHaveBeenCalledOnce();
	});
});
