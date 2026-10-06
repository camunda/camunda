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
import {afterAll, afterEach, beforeAll, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {replaceMonacoValue} from '#/shared-test-modules/monaco';
import {
	mockGetGlobalClusterVariableEndpoint,
	mockUpdateGlobalClusterVariableEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createClusterVariable,
	createClusterVariableSearchResult,
} from '#/shared-test-modules/api-mocks/cluster-variables';
import {EditClusterVariableModal} from './EditClusterVariableModal';

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

describe('<EditClusterVariableModal />', () => {
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

	it('should render nothing when there is no cluster variable to edit', async () => {
		const screen = await render(<EditClusterVariableModal clusterVariable={null} onClose={() => {}} />, {
			wrapper: getWrapper(),
		});

		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
	});

	it('should load the full value instead of the truncated list value', async ({worker}) => {
		worker.use(
			mockGetGlobalClusterVariableEndpoint({
				successResponse: HttpResponse.json(createClusterVariable({name: 'my-variable', value: '"the full value"'})),
			}),
		);
		const truncated = createClusterVariable({name: 'my-variable', value: '"the fu'});

		const screen = await render(<EditClusterVariableModal clusterVariable={truncated} onClose={() => {}} />, {
			wrapper: getWrapper(),
		});

		await expect.element(screen.getByRole('textbox', {name: 'Name'})).toHaveValue('my-variable');
		await expect.element(screen.getByRole('textbox', {name: 'Name'})).toBeDisabled();
		await expect.element(screen.getByText('"the full value"'), MONACO_LOAD).toBeInTheDocument();
	});

	it('should show an error when the value cannot be loaded', async ({worker}) => {
		worker.use(mockGetGlobalClusterVariableEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

		const screen = await render(
			<EditClusterVariableModal clusterVariable={createClusterVariable()} onClose={() => {}} />,
			{wrapper: getWrapper()},
		);

		await expect.element(screen.getByText('The value could not be loaded.')).toBeVisible();
	});

	it('should keep save disabled until the value changes', async ({worker}) => {
		worker.use(
			mockGetGlobalClusterVariableEndpoint({
				successResponse: HttpResponse.json(createClusterVariable({value: '1'})),
			}),
		);
		const screen = await render(
			<EditClusterVariableModal clusterVariable={createClusterVariable()} onClose={() => {}} />,
			{
				wrapper: getWrapper(),
			},
		);
		const editor = screen.getByRole('textbox', {name: 'Value'});
		await expect.element(editor, MONACO_LOAD).toBeInTheDocument();

		await expect.element(screen.getByRole('button', {name: 'Save'})).toBeDisabled();

		await replaceMonacoValue(editor, '2');

		await expect.element(screen.getByRole('button', {name: 'Save'})).toBeEnabled();
	});

	it('should update the cluster variable with the edited value', async ({worker}) => {
		worker.use(
			mockGetGlobalClusterVariableEndpoint({
				successResponse: HttpResponse.json(createClusterVariable({value: '1'})),
				once: true,
			}),
			mockGetGlobalClusterVariableEndpoint({
				successResponse: HttpResponse.json(createClusterVariable({value: '42'})),
			}),
			mockUpdateGlobalClusterVariableEndpoint({
				schema: z.object({value: z.literal(42)}).strict(),
				successResponse: HttpResponse.json(createClusterVariable({value: '42'})),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);
		const onClose = vi.fn();
		// Search results carry `isTruncated`, which must not leak into the update request body.
		const searchResult = createClusterVariableSearchResult({isTruncated: false});
		const screen = await render(<EditClusterVariableModal clusterVariable={searchResult} onClose={onClose} />, {
			wrapper: getWrapper(),
		});
		const editor = screen.getByRole('textbox', {name: 'Value'});
		await expect.element(editor, MONACO_LOAD).toBeInTheDocument();

		await replaceMonacoValue(editor, '42');
		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await vi.waitFor(() => expect(onClose).toHaveBeenCalledOnce());
	});

	it('should reject a value that is not valid JSON', async ({worker}) => {
		worker.use(
			mockGetGlobalClusterVariableEndpoint({
				successResponse: HttpResponse.json(createClusterVariable({value: '1'})),
			}),
		);
		const screen = await render(
			<EditClusterVariableModal clusterVariable={createClusterVariable()} onClose={() => {}} />,
			{
				wrapper: getWrapper(),
			},
		);
		const editor = screen.getByRole('textbox', {name: 'Value'});
		await expect.element(editor, MONACO_LOAD).toBeInTheDocument();

		await replaceMonacoValue(editor, 'nope');
		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await expect.element(screen.getByText('Value is invalid. It must be a string or valid JSON.')).toBeVisible();
	});

	it('should reject a null value', async ({worker}) => {
		worker.use(
			mockGetGlobalClusterVariableEndpoint({
				successResponse: HttpResponse.json(createClusterVariable({value: '1'})),
			}),
		);
		const screen = await render(
			<EditClusterVariableModal clusterVariable={createClusterVariable()} onClose={() => {}} />,
			{
				wrapper: getWrapper(),
			},
		);
		const editor = screen.getByRole('textbox', {name: 'Value'});
		await expect.element(editor, MONACO_LOAD).toBeInTheDocument();

		await replaceMonacoValue(editor, 'null');
		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await expect.element(screen.getByText('Value cannot be null')).toBeVisible();
	});

	it('should show an error toast and stay open when the update fails', async ({worker}) => {
		worker.use(
			mockGetGlobalClusterVariableEndpoint({
				successResponse: HttpResponse.json(createClusterVariable({value: '1'})),
			}),
			mockUpdateGlobalClusterVariableEndpoint({successResponse: new HttpResponse(null, {status: 500})}),
		);
		const onClose = vi.fn();
		const screen = await render(
			<EditClusterVariableModal clusterVariable={createClusterVariable()} onClose={onClose} />,
			{
				wrapper: getWrapper(),
			},
		);
		const editor = screen.getByRole('textbox', {name: 'Value'});
		await expect.element(editor, MONACO_LOAD).toBeInTheDocument();

		await replaceMonacoValue(editor, '2');
		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await expect.element(screen.getByText('Failed to update cluster variable')).toBeVisible();
		expect(onClose).not.toHaveBeenCalled();
	});
});
