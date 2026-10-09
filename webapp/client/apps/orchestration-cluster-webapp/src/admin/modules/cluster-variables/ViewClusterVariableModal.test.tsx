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
import {afterAll, afterEach, beforeAll, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {mockGetGlobalClusterVariableEndpoint} from '#/shared-test-modules/mock-handlers';
import {createClusterVariable} from '#/shared-test-modules/api-mocks/cluster-variables';
import {ViewClusterVariableModal} from './ViewClusterVariableModal';

const MONACO_LOAD = {timeout: 15_000};

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

describe('<ViewClusterVariableModal />', () => {
	beforeAll(() => {
		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout', 'Date'], shouldAdvanceTime: true});
	});
	afterAll(() => vi.useRealTimers());
	afterEach(async () => {
		// Closing Monaco before its word highlighter fires leaves an unhandled "Canceled" rejection.
		await vi.advanceTimersByTimeAsync(300);
		await cleanup();
		toast.dismiss();
	});

	it('should render nothing when there is no cluster variable to view', async () => {
		const screen = await render(<ViewClusterVariableModal clusterVariable={null} onClose={() => {}} />, {
			wrapper: getWrapper(),
		});

		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
	});

	it('should show the full value of the cluster variable read-only', async ({worker}) => {
		worker.use(
			mockGetGlobalClusterVariableEndpoint({
				successResponse: HttpResponse.json(createClusterVariable({name: 'my-variable', value: '"the full value"'})),
			}),
		);

		const screen = await render(
			<ViewClusterVariableModal
				clusterVariable={createClusterVariable({name: 'my-variable', value: '"the fu'})}
				onClose={() => {}}
			/>,
			{wrapper: getWrapper()},
		);

		await expect.element(screen.getByRole('heading', {name: 'my-variable'})).toBeVisible();
		await expect.element(screen.getByText('"the full value"'), MONACO_LOAD).toBeInTheDocument();
	});

	it('should show an error when the value cannot be loaded', async ({worker}) => {
		worker.use(mockGetGlobalClusterVariableEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

		const screen = await render(
			<ViewClusterVariableModal clusterVariable={createClusterVariable()} onClose={() => {}} />,
			{wrapper: getWrapper()},
		);

		await expect.element(screen.getByText('The value could not be loaded.')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Copy value'})).toBeDisabled();
	});

	it('should call onClose when closed', async ({worker}) => {
		worker.use(mockGetGlobalClusterVariableEndpoint({successResponse: HttpResponse.json(createClusterVariable())}));
		const onClose = vi.fn();
		const screen = await render(
			<ViewClusterVariableModal clusterVariable={createClusterVariable()} onClose={onClose} />,
			{wrapper: getWrapper()},
		);
		await expect.element(screen.getByRole('textbox', {name: 'Value'}), MONACO_LOAD).toBeInTheDocument();

		await userEvent.click(screen.getByRole('button', {name: 'Close'}).last());

		expect(onClose).toHaveBeenCalledOnce();
	});
});
