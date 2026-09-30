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
import {mockUpdateMappingRuleEndpoint} from '#/shared-test-modules/mock-handlers';
import {createMappingRule} from '#/shared-test-modules/api-mocks/mapping-rules';
import {EditMappingRuleModal} from './EditMappingRuleModal';

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

describe('<EditMappingRuleModal />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should render nothing when there is no mapping rule to edit', async () => {
		const screen = await render(<EditMappingRuleModal mappingRule={null} onClose={() => {}} />, {
			wrapper: getWrapper(),
		});

		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
	});

	it('should prefill the fields with the mapping rule being edited', async () => {
		const mappingRule = createMappingRule({mappingRuleId: 'my-rule', name: 'My rule', claimName: 'email'});
		const screen = await render(<EditMappingRuleModal mappingRule={mappingRule} onClose={() => {}} />, {
			wrapper: getWrapper(),
		});

		await expect.element(screen.getByRole('textbox', {name: 'Mapping rule ID'})).toHaveValue('my-rule');
		await expect.element(screen.getByRole('textbox', {name: 'Mapping rule ID'})).toBeDisabled();
		await expect.element(screen.getByRole('textbox', {name: 'Name'})).toHaveValue('My rule');
		await expect.element(screen.getByRole('textbox', {name: 'Claim name'})).toHaveValue('email');
	});

	it('should update the mapping rule with the edited values', async ({worker}) => {
		const mockOnClose = vi.fn();
		const mappingRule = createMappingRule({mappingRuleId: 'my-rule'});
		worker.use(
			mockUpdateMappingRuleEndpoint({
				schema: z.object({name: z.literal('Updated name'), claimName: z.string(), claimValue: z.string()}),
				successResponse: HttpResponse.json({}),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		const screen = await render(<EditMappingRuleModal mappingRule={mappingRule} onClose={mockOnClose} />, {
			wrapper: getWrapper(),
		});

		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'Updated name');
		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await vi.waitFor(() => expect(mockOnClose).toHaveBeenCalledOnce());
	});

	it('should call onClose when cancelled', async () => {
		const mockOnClose = vi.fn();
		const mappingRule = createMappingRule();
		const screen = await render(<EditMappingRuleModal mappingRule={mappingRule} onClose={mockOnClose} />, {
			wrapper: getWrapper(),
		});

		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}).last());

		expect(mockOnClose).toHaveBeenCalledOnce();
	});
});
