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
import {mockCreateMappingRuleEndpoint} from '#/shared-test-modules/mock-handlers';
import {AddMappingRuleModal} from './AddMappingRuleModal';

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

describe('<AddMappingRuleModal />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should create a mapping rule with the entered values', async ({worker}) => {
		const mockOnClose = vi.fn();
		worker.use(
			mockCreateMappingRuleEndpoint({
				schema: z.object({
					mappingRuleId: z.literal('my-rule'),
					name: z.literal('My rule'),
					claimName: z.literal('email'),
					claimValue: z.literal('demo@example.com'),
				}),
				successResponse: HttpResponse.json({}),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		const screen = await render(<AddMappingRuleModal isOpen onClose={mockOnClose} />, {wrapper: getWrapper()});

		await userEvent.fill(screen.getByRole('textbox', {name: 'Mapping rule ID'}), 'my-rule');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'My rule');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Claim name'}), 'email');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Claim value'}), 'demo@example.com');
		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await vi.waitFor(() => expect(mockOnClose).toHaveBeenCalledOnce());
	});

	it('should show validation errors when submitting with empty fields', async () => {
		const screen = await render(<AddMappingRuleModal isOpen onClose={() => {}} />, {wrapper: getWrapper()});

		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await expect
			.element(screen.getByRole('textbox', {name: 'Mapping rule ID'}))
			.toHaveAttribute('aria-invalid', 'true');
		await expect.element(screen.getByRole('textbox', {name: 'Name'})).toHaveAttribute('aria-invalid', 'true');
		await expect.element(screen.getByRole('textbox', {name: 'Claim name'})).toHaveAttribute('aria-invalid', 'true');
		await expect.element(screen.getByRole('textbox', {name: 'Claim value'})).toHaveAttribute('aria-invalid', 'true');
	});

	it('should show a validation error for an invalid mapping rule ID', async () => {
		const screen = await render(<AddMappingRuleModal isOpen onClose={() => {}} />, {wrapper: getWrapper()});

		await userEvent.fill(screen.getByRole('textbox', {name: 'Mapping rule ID'}), 'invalid id!');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'My rule');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Claim name'}), 'email');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Claim value'}), 'demo@example.com');
		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await expect
			.element(screen.getByText('ID must be 256 characters or fewer, using only letters, numbers, and _ ~ @ . + -'))
			.toBeVisible();
	});

	it('should show an inline error when the mapping rule ID already exists', async ({worker}) => {
		worker.use(
			mockCreateMappingRuleEndpoint({
				successResponse: HttpResponse.json(
					{
						type: 'about:blank',
						title: 'ALREADY_EXISTS',
						status: 409,
						detail:
							"Expected to create mapping rule with id 'my-rule', but a mapping rule with this id already exists.",
						instance: '/v2/mapping-rules',
					},
					{status: 409},
				),
			}),
		);

		const screen = await render(<AddMappingRuleModal isOpen onClose={() => {}} />, {wrapper: getWrapper()});

		await userEvent.fill(screen.getByRole('textbox', {name: 'Mapping rule ID'}), 'my-rule');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'My rule');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Claim name'}), 'email');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Claim value'}), 'demo@example.com');
		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await expect.element(screen.getByText('A mapping rule with this ID already exists')).toBeVisible();
	});

	it('should show a generic error toast when the claim already exists on another mapping rule', async ({worker}) => {
		worker.use(
			mockCreateMappingRuleEndpoint({
				successResponse: HttpResponse.json(
					{
						type: 'about:blank',
						title: 'ALREADY_EXISTS',
						status: 409,
						detail:
							"Expected to create mapping rule with claimName 'email' and claimValue 'demo@example.com', but a mapping rule with this claim already exists.",
						instance: '/v2/mapping-rules',
					},
					{status: 409},
				),
			}),
		);

		const screen = await render(<AddMappingRuleModal isOpen onClose={() => {}} />, {wrapper: getWrapper()});

		await userEvent.fill(screen.getByRole('textbox', {name: 'Mapping rule ID'}), 'my-rule');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'My rule');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Claim name'}), 'email');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Claim value'}), 'demo@example.com');
		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await expect.element(screen.getByText('Failed to create mapping rule')).toBeVisible();
	});

	it('should call onClose when cancelled', async () => {
		const mockOnClose = vi.fn();
		const screen = await render(<AddMappingRuleModal isOpen onClose={mockOnClose} />, {wrapper: getWrapper()});

		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}).last());

		expect(mockOnClose).toHaveBeenCalledOnce();
	});
});
