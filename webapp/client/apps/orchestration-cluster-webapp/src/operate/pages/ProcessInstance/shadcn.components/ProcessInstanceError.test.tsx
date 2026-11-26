/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {cleanup} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {ForbiddenError} from '#/shared/errors';
import {ProcessInstanceError} from './ProcessInstanceError';

const PROCESS_INSTANCE_ID = '2251799813685249';
const PATH = '/operate-preview/processes/$processInstanceId';

function renderError(error: unknown) {
	return renderWithRouter(
		() => <ProcessInstanceError error={error} processInstanceId={PROCESS_INSTANCE_ID} onRetry={() => {}} />,
		{path: PATH, initialEntry: `/operate-preview/processes/${PROCESS_INSTANCE_ID}`},
	);
}

describe('<ProcessInstanceError /> (DS)', () => {
	afterEach(async () => {
		await cleanup();
	});

	it('should show the permission message for a forbidden error', async () => {
		const screen = await renderError(new ForbiddenError());

		await expect
			.element(screen.getByRole('heading', {name: 'You do not have permission to view this information'}))
			.toBeVisible();
		await expect.element(screen.getByText('Contact your administrator to get access.')).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'Learn more about permissions'})).toBeVisible();
	});

	it.for([400, 404])(
		'should leave for the process instances list when the instance is not found (%s)',
		async (status) => {
			const screen = await renderError({
				variant: 'failed-response',
				response: new Response(null, {status}),
				networkError: null,
			});

			await expect.poll(() => screen.router.state.location.pathname).toBe('/operate-preview/processes');
		},
	);

	it('should offer a retry for any other failed response', async () => {
		const screen = await renderError({
			variant: 'failed-response',
			response: new Response(null, {status: 500}),
			networkError: null,
		});

		await expect.element(screen.getByRole('heading', {name: 'Something went wrong'})).toBeVisible();
	});
});
