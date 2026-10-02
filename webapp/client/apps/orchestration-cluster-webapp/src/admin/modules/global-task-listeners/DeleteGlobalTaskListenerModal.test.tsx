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
import {cleanup, render} from 'vitest-browser-react';
import {afterEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {mockDeleteGlobalTaskListenerEndpoint} from '#/shared-test-modules/mock-handlers';
import {createGlobalTaskListener} from '#/shared-test-modules/api-mocks/global-task-listeners';
import {DeleteGlobalTaskListenerModal} from './DeleteGlobalTaskListenerModal';

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

describe('<DeleteGlobalTaskListenerModal />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should render nothing when there is no global task listener to delete', async () => {
		const screen = await render(<DeleteGlobalTaskListenerModal globalTaskListener={null} onClose={() => {}} />, {
			wrapper: getWrapper(),
		});

		await expect.element(screen.getByRole('alertdialog')).not.toBeInTheDocument();
	});

	it('should show the global task listener ID in the confirmation message', async () => {
		const globalTaskListener = createGlobalTaskListener({id: 'my-listener'});
		const screen = await render(
			<DeleteGlobalTaskListenerModal globalTaskListener={globalTaskListener} onClose={() => {}} />,
			{wrapper: getWrapper()},
		);

		await expect.element(screen.getByText('my-listener')).toBeVisible();
	});

	it('should delete the global task listener on confirm', async ({worker}) => {
		const mockOnClose = vi.fn();
		const globalTaskListener = createGlobalTaskListener({id: 'my-listener'});
		worker.use(mockDeleteGlobalTaskListenerEndpoint({successResponse: new HttpResponse(null, {status: 204})}));

		const screen = await render(
			<DeleteGlobalTaskListenerModal globalTaskListener={globalTaskListener} onClose={mockOnClose} />,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));

		await vi.waitFor(() => expect(mockOnClose).toHaveBeenCalledOnce());
	});

	it('should keep the dialog open when the delete fails', async ({worker}) => {
		const mockOnClose = vi.fn();
		const globalTaskListener = createGlobalTaskListener({id: 'my-listener'});
		worker.use(mockDeleteGlobalTaskListenerEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

		const screen = await render(
			<DeleteGlobalTaskListenerModal globalTaskListener={globalTaskListener} onClose={mockOnClose} />,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));

		await expect.element(screen.getByText('Failed to delete global task listener')).toBeVisible();
		expect(mockOnClose).not.toHaveBeenCalled();
	});

	it('should call onClose when cancelled', async () => {
		const mockOnClose = vi.fn();
		const globalTaskListener = createGlobalTaskListener();
		const screen = await render(
			<DeleteGlobalTaskListenerModal globalTaskListener={globalTaskListener} onClose={mockOnClose} />,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}));

		expect(mockOnClose).toHaveBeenCalledOnce();
	});
});
