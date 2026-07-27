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
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {TaskDetailsHistoryPage} from './TaskDetailsHistoryPage';

const auditLogs = [
	createAuditLog({auditLogKey: 'create-log', operationType: 'CREATE'}),
	createAuditLog({auditLogKey: 'assign-log', operationType: 'ASSIGN', relatedEntityKey: 'demo'}),
];

const userTaskKey = '2251799813685281';

describe('<TaskDetailsHistoryPage />', () => {
	it('should tell the user when the task has no history', async () => {
		const screen = await render(
			<TaskDetailsHistoryPage
				userTaskKey={userTaskKey}
				auditLogs={[]}
				search={{sort: 'timestamp+desc'}}
				onScrollDown={vi.fn()}
			/>,
		);

		await expect.element(screen.getByText('No history entries found for this task')).toBeVisible();
	});

	it('should show the task history when entries exist', async () => {
		const screen = await renderWithRouter(
			() => (
				<TaskDetailsHistoryPage
					userTaskKey={userTaskKey}
					auditLogs={auditLogs}
					search={{sort: 'timestamp+desc'}}
					onScrollDown={vi.fn()}
				/>
			),
			{path: '/tasklist/$userTaskKey/history', initialEntry: '/tasklist/2251799813685281/history'},
		);

		await expect.element(screen.getByTestId('history-tab-content')).toBeVisible();
		await expect.element(screen.getByText('Create task')).toBeVisible();
		await expect.element(screen.getByText('Assign task')).toBeVisible();
	});

	it('should load more history entries when the user reaches the end of the list', async () => {
		const onScrollDown = vi.fn();
		const screen = await renderWithRouter(
			() => (
				<div style={{height: '100px', display: 'flex'}}>
					<TaskDetailsHistoryPage
						userTaskKey={userTaskKey}
						auditLogs={auditLogs}
						search={{sort: 'timestamp+desc'}}
						onScrollDown={onScrollDown}
					/>
				</div>
			),
			{path: '/tasklist/$userTaskKey/history', initialEntry: '/tasklist/2251799813685281/history'},
		);

		await userEvent.wheel(screen.getByTestId('history-scroll-container'), {delta: {y: 10000}});

		expect(onScrollDown).toHaveBeenCalled();
	});
});
