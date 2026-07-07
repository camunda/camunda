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
import {TaskDetailsProcessRouteError} from './TaskDetailsProcessError';

function failedResponseError(status: number) {
	return {variant: 'failed-response', response: new Response(null, {status}), networkError: null} as unknown as Error;
}

describe('<TaskDetailsProcessError />', () => {
	it('should explain when the user does not have permission to view the process', async () => {
		const screen = await render(
			<TaskDetailsProcessRouteError error={failedResponseError(403)} info={{componentStack: ''}} reset={vi.fn()} />,
		);

		await expect.element(screen.getByText("You don't have permission to view the process")).toBeVisible();
		await expect
			.element(screen.getByText("You don't have the necessary permissions. Contact your admin to request access."))
			.toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Try again'})).not.toBeInTheDocument();
	});

	it('should allow users to retry when the process diagram cannot be loaded', async () => {
		const reset = vi.fn();
		const screen = await render(
			<TaskDetailsProcessRouteError error={failedResponseError(500)} info={{componentStack: ''}} reset={reset} />,
		);

		await expect.element(screen.getByText('Process could not be loaded')).toBeVisible();
		await expect
			.element(screen.getByText('The BPMN process diagram could not be loaded. Please try again later.'))
			.toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

		expect(reset).toHaveBeenCalledOnce();
	});
});
