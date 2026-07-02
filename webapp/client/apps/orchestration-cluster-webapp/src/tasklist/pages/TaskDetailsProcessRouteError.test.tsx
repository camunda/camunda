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
import {EmptyProcessXmlError} from '#/shared/errors';
import {TaskDetailsProcessRouteError} from './TaskDetailsProcessRouteError';

function failedResponseError(status: number) {
	return {variant: 'failed-response', response: new Response(null, {status}), networkError: null} as unknown as Error;
}

function networkError() {
	return {variant: 'network-error', response: null, networkError: new Error('Failed to fetch')} as unknown as Error;
}

describe('<TaskDetailsProcessRouteError />', () => {
	it('should show the permission error when process access is forbidden', async () => {
		const screen = await render(
			<TaskDetailsProcessRouteError error={failedResponseError(403)} info={{componentStack: ''}} reset={vi.fn()} />,
		);

		await expect.element(screen.getByText("You don't have permission to view the process")).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Try again'})).not.toBeInTheDocument();
	});

	it('should show a recoverable loading error when the process request fails', async () => {
		const screen = await render(
			<TaskDetailsProcessRouteError error={failedResponseError(500)} info={{componentStack: ''}} reset={vi.fn()} />,
		);

		await expect.element(screen.getByText('Process could not be loaded')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Try again'})).toBeVisible();
	});

	it('should show a recoverable loading error when the process request cannot reach the server', async () => {
		const screen = await render(
			<TaskDetailsProcessRouteError error={networkError()} info={{componentStack: ''}} reset={vi.fn()} />,
		);

		await expect.element(screen.getByText('Process could not be loaded')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Try again'})).toBeVisible();
	});

	it('should show a recoverable loading error when the process definition has no diagram XML', async () => {
		const screen = await render(
			<TaskDetailsProcessRouteError error={new EmptyProcessXmlError()} info={{componentStack: ''}} reset={vi.fn()} />,
		);

		await expect.element(screen.getByText('Process could not be loaded')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Try again'})).toBeVisible();
	});

	it('should allow users to retry after a recoverable loading error', async () => {
		const reset = vi.fn();
		const screen = await render(
			<TaskDetailsProcessRouteError error={failedResponseError(500)} info={{componentStack: ''}} reset={reset} />,
		);

		await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

		expect(reset).toHaveBeenCalledOnce();
	});
});
