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
import {
	mockDeleteGlobalClusterVariableEndpoint,
	mockGetGlobalClusterVariableEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createClusterVariable} from '#/shared-test-modules/api-mocks/cluster-variables';
import {DeleteClusterVariableModal} from './DeleteClusterVariableModal';

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

describe('<DeleteClusterVariableModal />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should render nothing when there is no cluster variable to delete', async () => {
		const screen = await render(<DeleteClusterVariableModal clusterVariable={null} onClose={() => {}} />, {
			wrapper: getWrapper(),
		});

		await expect.element(screen.getByRole('alertdialog')).not.toBeInTheDocument();
	});

	it('should show the cluster variable name in the confirmation message', async () => {
		const screen = await render(
			<DeleteClusterVariableModal clusterVariable={createClusterVariable({name: 'my-variable'})} onClose={() => {}} />,
			{wrapper: getWrapper()},
		);

		await expect.element(screen.getByRole('alertdialog').getByText('my-variable')).toBeVisible();
	});

	it('should delete the cluster variable on confirm', async ({worker}) => {
		const onClose = vi.fn();
		worker.use(
			mockDeleteGlobalClusterVariableEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetGlobalClusterVariableEndpoint({successResponse: new HttpResponse(null, {status: 404})}),
		);
		const screen = await render(
			<DeleteClusterVariableModal clusterVariable={createClusterVariable()} onClose={onClose} />,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));

		await vi.waitFor(() => expect(onClose).toHaveBeenCalledOnce());
	});

	it('should keep the dialog open when the delete fails', async ({worker}) => {
		const onClose = vi.fn();
		worker.use(mockDeleteGlobalClusterVariableEndpoint({successResponse: new HttpResponse(null, {status: 500})}));
		const screen = await render(
			<DeleteClusterVariableModal clusterVariable={createClusterVariable()} onClose={onClose} />,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));

		await expect.element(screen.getByText('Failed to delete cluster variable')).toBeVisible();
		expect(onClose).not.toHaveBeenCalled();
	});

	it('should call onClose when cancelled', async () => {
		const onClose = vi.fn();
		const screen = await render(
			<DeleteClusterVariableModal clusterVariable={createClusterVariable()} onClose={onClose} />,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}));

		expect(onClose).toHaveBeenCalledOnce();
	});
});
