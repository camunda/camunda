/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, vi} from 'vitest';
import {cleanup} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {ForbiddenError} from '#/shared/errors';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {ProcessInstanceError} from './ProcessInstanceError';

const PROCESS_INSTANCE_ID = '2251799813685249';
const PATH = '/operate-preview/processes/$processInstanceId';

function renderError(error: unknown, onRetry = vi.fn()) {
	return renderWithRouter(
		() => <ProcessInstanceError error={error} processInstanceId={PROCESS_INSTANCE_ID} onRetry={onRetry} />,
		{path: PATH, initialEntry: `/operate-preview/processes/${PROCESS_INSTANCE_ID}`},
	);
}

describe('<ProcessInstanceError /> (DS)', () => {
	afterEach(async () => {
		await cleanup();
		notificationsStore.reset();
	});

	it('should show the permission message for a forbidden error', async () => {
		const screen = await renderError(new ForbiddenError());

		await expect
			.element(screen.getByRole('heading', {name: 'You do not have permission to view this information'}))
			.toBeVisible();
		await expect.element(screen.getByText('Contact your administrator to get access.')).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'Learn more about permissions'})).toBeVisible();
	});

	it('should notify and redirect to the process list for a not found error', async () => {
		const screen = await renderError({
			variant: 'failed-response',
			response: new Response(null, {status: 404}),
			networkError: null,
		});

		await expect.poll(() => screen.router.state.location.pathname).toBe('/operate-preview/processes');
		expect(notificationsStore.notifications).toEqual([
			expect.objectContaining({
				kind: 'error',
				title: `Instance ${PROCESS_INSTANCE_ID} could not be found`,
			}),
		]);
	});

	it('should offer a retry for any other error', async () => {
		const onRetry = vi.fn();
		const screen = await renderError(new Error('boom'), onRetry);

		await screen.getByRole('button', {name: 'Try again'}).click();

		expect(onRetry).toHaveBeenCalledOnce();
	});
});
