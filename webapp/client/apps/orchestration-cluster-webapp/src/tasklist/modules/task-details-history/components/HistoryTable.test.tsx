/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {createAuditLog} from '#/shared-test-modules/api-mocks/audit-logs';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {HistoryTable} from './HistoryTable';

const auditLogs = [
	createAuditLog({
		auditLogKey: 'create-log',
		operationType: 'CREATE',
		actorId: 'alice',
		timestamp: '2024-01-01T10:00:00.000Z',
	}),
	createAuditLog({
		auditLogKey: 'assign-log',
		operationType: 'ASSIGN',
		actorId: 'jane',
		relatedEntityKey: 'demo',
		timestamp: '2024-01-02T10:00:00.000Z',
	}),
];

const userTaskKey = '2251799813685281';

describe('<HistoryTable />', () => {
	it('should show the task history entries', async () => {
		const screen = await renderWithRouter(
			() => <HistoryTable userTaskKey={userTaskKey} auditLogs={auditLogs} search={{sort: 'timestamp+desc'}} />,
			{path: '/tasklist/$userTaskKey/history', initialEntry: '/tasklist/2251799813685281/history'},
		);

		await expect.element(screen.getByRole('columnheader', {name: /sort by operation type/i})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: /^details$/i})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: /sort by actor/i})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: /sort by date/i})).toBeVisible();
		await expect.element(screen.getByText('Create task')).toBeVisible();
		await expect.element(screen.getByText('Assign task')).toBeVisible();
		await expect.element(screen.getByText('jane')).toBeVisible();
	});

	it('should show assignee details for assignment entries', async () => {
		const screen = await renderWithRouter(
			() => <HistoryTable userTaskKey={userTaskKey} auditLogs={auditLogs} search={{sort: 'timestamp+desc'}} />,
			{path: '/tasklist/$userTaskKey/history', initialEntry: '/tasklist/2251799813685281/history'},
		);

		await expect.element(screen.getByText('Assignee')).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Assignee demo'})).toBeVisible();
	});

	it('should show sortable columns for history entries', async () => {
		const screen = await renderWithRouter(
			() => <HistoryTable userTaskKey={userTaskKey} auditLogs={auditLogs} search={{sort: 'timestamp+desc'}} />,
			{path: '/tasklist/$userTaskKey/history', initialEntry: '/tasklist/2251799813685281/history'},
		);

		await expect.element(screen.getByRole('columnheader', {name: /sort by operation type/i})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: /sort by actor/i})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: /sort by date/i})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: /^details$/i})).toBeVisible();
	});

	it('should open a history entry from the details action', async () => {
		const {router, ...screen} = await renderWithRouter(
			() => <HistoryTable userTaskKey={userTaskKey} auditLogs={auditLogs} search={{sort: 'timestamp+desc'}} />,
			{path: '/tasklist/$userTaskKey/history', initialEntry: '/tasklist/2251799813685281/history'},
		);

		await userEvent.click(screen.getByRole('link', {name: 'Open details'}).first());

		await expect.poll(() => router.state.location.pathname).toBe('/tasklist/2251799813685281/history/create-log');
	});

	it('should preserve the history search params when opening details', async () => {
		const {router, ...screen} = await renderWithRouter(
			() => <HistoryTable userTaskKey={userTaskKey} auditLogs={auditLogs} search={{sort: 'actorId+asc'}} />,
			{
				path: '/tasklist/$userTaskKey/history',
				initialEntry: '/tasklist/2251799813685281/history?sort=actorId+asc',
			},
		);

		await userEvent.click(screen.getByRole('link', {name: 'Open details'}).first());

		await expect.poll(() => router.state.location.pathname).toBe('/tasklist/2251799813685281/history/create-log');
		expect(router.state.location.search).toEqual({sort: 'actorId+asc'});
	});

	it('should sort by operation type when the user selects the operation column', async () => {
		const {router, ...screen} = await renderWithRouter(
			() => <HistoryTable userTaskKey={userTaskKey} auditLogs={auditLogs} search={{sort: 'timestamp+desc'}} />,
			{path: '/tasklist/$userTaskKey/history', initialEntry: '/tasklist/2251799813685281/history'},
		);

		await userEvent.click(screen.getByRole('columnheader', {name: /sort by operation type/i}));

		await expect.poll(() => router.state.location.search).toEqual({sort: 'operationType+asc'});
	});

	it('should sort by actor when the user selects the actor column', async () => {
		const {router, ...screen} = await renderWithRouter(
			() => <HistoryTable userTaskKey={userTaskKey} auditLogs={auditLogs} search={{sort: 'timestamp+desc'}} />,
			{path: '/tasklist/$userTaskKey/history', initialEntry: '/tasklist/2251799813685281/history'},
		);

		await userEvent.click(screen.getByRole('columnheader', {name: /sort by actor/i}));

		await expect.poll(() => router.state.location.search).toEqual({sort: 'actorId+asc'});
	});

	it('should sort by date when the user selects the date column', async () => {
		const {router, ...screen} = await renderWithRouter(
			() => <HistoryTable userTaskKey={userTaskKey} auditLogs={auditLogs} search={{sort: 'timestamp+asc'}} />,
			{
				path: '/tasklist/$userTaskKey/history',
				initialEntry: '/tasklist/2251799813685281/history?sort=timestamp+asc',
			},
		);

		await userEvent.click(screen.getByRole('columnheader', {name: /sort by date/i}));

		await expect.poll(() => router.state.location.search).toEqual({sort: 'timestamp+desc'});
	});
});
