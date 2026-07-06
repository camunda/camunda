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
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {TaskDetailsHistoryErrorPage} from './TaskDetailsHistoryErrorPage';

function failedResponseError(status: number) {
	return {variant: 'failed-response', response: new Response(null, {status}), networkError: null} as unknown as Error;
}

function networkError() {
	return {variant: 'network-error', response: null, networkError: new Error('Failed to fetch')} as unknown as Error;
}

describe('<TaskDetailsHistoryErrorPage />', () => {
	it('should tell the user when task history cannot be loaded', async () => {
		const screen = await renderWithRouter(
			() => <TaskDetailsHistoryErrorPage error={networkError()} info={{componentStack: ''}} reset={vi.fn()} />,
			{path: '/tasklist/$userTaskKey/history', initialEntry: '/tasklist/2251799813685281/history'},
		);

		await expect.element(screen.getByText('Something went wrong')).toBeVisible();
		await expect.element(screen.getByText(/could not load the task history/i)).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Try again'})).toBeVisible();
	});

	it('should let the user try loading task history again', async () => {
		const reset = vi.fn();
		const screen = await renderWithRouter(
			() => <TaskDetailsHistoryErrorPage error={networkError()} info={{componentStack: ''}} reset={reset} />,
			{path: '/tasklist/$userTaskKey/history', initialEntry: '/tasklist/2251799813685281/history'},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

		expect(reset).toHaveBeenCalledOnce();
	});

	it('should tell the user when they do not have permission to view task history', async () => {
		const screen = await render(
			<TaskDetailsHistoryErrorPage error={failedResponseError(403)} info={{componentStack: ''}} reset={vi.fn()} />,
		);

		await expect.element(screen.getByText("You don't have permission to view task history")).toBeVisible();
		await expect.element(screen.getByText(/contact your cluster admin/i)).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Try again'})).not.toBeInTheDocument();
	});

	it('should help the user learn about required permissions', async () => {
		const screen = await render(
			<TaskDetailsHistoryErrorPage error={failedResponseError(403)} info={{componentStack: ''}} reset={vi.fn()} />,
		);

		await expect.element(screen.getByRole('link', {name: /learn more about roles and permissions/i})).toBeVisible();
	});
});
