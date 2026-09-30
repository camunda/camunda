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
import {mockDeleteMappingRuleEndpoint} from '#/shared-test-modules/mock-handlers';
import {createMappingRule} from '#/shared-test-modules/api-mocks/mapping-rules';
import {DeleteMappingRuleModal} from './DeleteMappingRuleModal';

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

describe('<DeleteMappingRuleModal />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should render nothing when there is no mapping rule to delete', async () => {
		const screen = await render(<DeleteMappingRuleModal mappingRule={null} onClose={() => {}} />, {
			wrapper: getWrapper(),
		});

		await expect.element(screen.getByRole('alertdialog')).not.toBeInTheDocument();
	});

	it('should show the mapping rule name in the confirmation message', async () => {
		const mappingRule = createMappingRule({name: 'My mapping rule'});
		const screen = await render(<DeleteMappingRuleModal mappingRule={mappingRule} onClose={() => {}} />, {
			wrapper: getWrapper(),
		});

		await expect.element(screen.getByText('My mapping rule')).toBeVisible();
	});

	it('should delete the mapping rule on confirm', async ({worker}) => {
		const mockOnClose = vi.fn();
		const mappingRule = createMappingRule({mappingRuleId: 'my-rule'});
		worker.use(mockDeleteMappingRuleEndpoint({successResponse: new HttpResponse(null, {status: 204})}));

		const screen = await render(<DeleteMappingRuleModal mappingRule={mappingRule} onClose={mockOnClose} />, {
			wrapper: getWrapper(),
		});

		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));

		await vi.waitFor(() => expect(mockOnClose).toHaveBeenCalledOnce());
	});

	it('should call onClose when cancelled', async () => {
		const mockOnClose = vi.fn();
		const mappingRule = createMappingRule();
		const screen = await render(<DeleteMappingRuleModal mappingRule={mappingRule} onClose={mockOnClose} />, {
			wrapper: getWrapper(),
		});

		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}));

		expect(mockOnClose).toHaveBeenCalledOnce();
	});
});
