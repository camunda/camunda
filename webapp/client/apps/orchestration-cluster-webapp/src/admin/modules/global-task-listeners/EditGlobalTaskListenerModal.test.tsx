/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {Toaster, toast} from '@camunda/design-system';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {cleanup, render} from 'vitest-browser-react';
import {afterEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {mockUpdateGlobalTaskListenerEndpoint} from '#/shared-test-modules/mock-handlers';
import {createGlobalTaskListener} from '#/shared-test-modules/api-mocks/global-task-listeners';
import {EditGlobalTaskListenerModal} from './EditGlobalTaskListenerModal';

function getWrapper() {
	const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});

	const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
		<QueryClientProvider client={queryClient}>
			{children}
			<Toaster />
		</QueryClientProvider>
	);

	return Wrapper;
}

describe('<EditGlobalTaskListenerModal />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should render nothing when there is no global task listener to edit', async () => {
		const screen = await render(<EditGlobalTaskListenerModal globalTaskListener={null} onClose={() => {}} />, {
			wrapper: getWrapper(),
		});

		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
	});

	it('should prefill the fields with the global task listener being edited', async () => {
		const globalTaskListener = createGlobalTaskListener({id: 'my-listener', type: 'my-type'});
		const screen = await render(
			<EditGlobalTaskListenerModal globalTaskListener={globalTaskListener} onClose={() => {}} />,
			{wrapper: getWrapper()},
		);

		await expect.element(screen.getByRole('textbox', {name: 'Listener ID'})).toHaveValue('my-listener');
		await expect.element(screen.getByRole('textbox', {name: 'Listener ID'})).toBeDisabled();
		await expect.element(screen.getByRole('textbox', {name: 'Listener type'})).toHaveValue('my-type');
	});

	it('should update the global task listener with the edited values', async ({worker}) => {
		const mockOnClose = vi.fn();
		const globalTaskListener = createGlobalTaskListener({id: 'my-listener'});
		worker.use(
			mockUpdateGlobalTaskListenerEndpoint({
				schema: z.object({
					type: z.literal('updated-type'),
					eventTypes: z.array(z.literal('creating')),
					retries: z.literal(3),
					afterNonGlobal: z.literal(false),
					priority: z.literal(50),
				}),
				successResponse: HttpResponse.json({}),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		const screen = await render(
			<EditGlobalTaskListenerModal globalTaskListener={globalTaskListener} onClose={mockOnClose} />,
			{wrapper: getWrapper()},
		);

		await userEvent.fill(screen.getByRole('textbox', {name: 'Listener type'}), 'updated-type');
		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await vi.waitFor(() => expect(mockOnClose).toHaveBeenCalledOnce());
	});

	it('should keep the dialog open when the update fails', async ({worker}) => {
		const mockOnClose = vi.fn();
		const globalTaskListener = createGlobalTaskListener({id: 'my-listener'});
		worker.use(
			mockUpdateGlobalTaskListenerEndpoint({
				schema: z.object({type: z.literal('will-not-match')}),
				successResponse: HttpResponse.json({}),
				failureResponse: new HttpResponse(null, {status: 500}),
			}),
		);

		const screen = await render(
			<EditGlobalTaskListenerModal globalTaskListener={globalTaskListener} onClose={mockOnClose} />,
			{wrapper: getWrapper()},
		);

		await userEvent.fill(screen.getByRole('textbox', {name: 'Listener type'}), 'updated-type');
		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await expect.element(screen.getByText('Failed to update global task listener')).toBeVisible();
		expect(mockOnClose).not.toHaveBeenCalled();
	});

	it('should call onClose when cancelled', async () => {
		const mockOnClose = vi.fn();
		const globalTaskListener = createGlobalTaskListener();
		const screen = await render(
			<EditGlobalTaskListenerModal globalTaskListener={globalTaskListener} onClose={mockOnClose} />,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}).last());

		expect(mockOnClose).toHaveBeenCalledOnce();
	});
});
