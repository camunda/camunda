/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {mockGetProcessInstanceCallHierarchyEndpoint} from '#/shared-test-modules/mock-handlers';
import {CancelConfirmationModal} from './CancelConfirmationModal';

const PROCESS_INSTANCE_KEY = 'instance_1';

function getWrapper() {
	const queryClient = new QueryClient({
		defaultOptions: {
			queries: {retry: false},
		},
	});

	const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
		<QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
	);

	return Wrapper;
}

describe('<CancelConfirmationModal />', () => {
	it('should call onConfirm without also calling onCancel when the apply button is clicked', async ({worker}) => {
		worker.use(mockGetProcessInstanceCallHierarchyEndpoint({successResponse: HttpResponse.json([])}));
		const onConfirm = vi.fn();
		const onCancel = vi.fn();

		const screen = await render(
			<CancelConfirmationModal
				processInstanceKey={PROCESS_INSTANCE_KEY}
				open
				onConfirm={onConfirm}
				onCancel={onCancel}
			/>,
			{wrapper: getWrapper()},
		);

		const applyButton = screen.getByRole('button', {name: 'Apply', exact: true});
		await expect.element(applyButton).toBeEnabled();
		await userEvent.click(applyButton);

		expect(onConfirm).toHaveBeenCalledOnce();
		expect(onCancel).not.toHaveBeenCalled();
	});

	it('should call onCancel without calling onConfirm when the cancel button is clicked', async ({worker}) => {
		worker.use(mockGetProcessInstanceCallHierarchyEndpoint({successResponse: HttpResponse.json([])}));
		const onConfirm = vi.fn();
		const onCancel = vi.fn();

		const screen = await render(
			<CancelConfirmationModal
				processInstanceKey={PROCESS_INSTANCE_KEY}
				open
				onConfirm={onConfirm}
				onCancel={onCancel}
			/>,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Cancel', exact: true}));

		expect(onCancel).toHaveBeenCalledOnce();
		expect(onConfirm).not.toHaveBeenCalled();
	});
});
