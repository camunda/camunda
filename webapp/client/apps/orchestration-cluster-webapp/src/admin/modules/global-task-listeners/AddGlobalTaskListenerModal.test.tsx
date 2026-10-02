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
import {mockCreateGlobalTaskListenerEndpoint} from '#/shared-test-modules/mock-handlers';
import {AddGlobalTaskListenerModal} from './AddGlobalTaskListenerModal';

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

describe('<AddGlobalTaskListenerModal />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should create a global task listener with the entered values', async ({worker}) => {
		const mockOnClose = vi.fn();
		worker.use(
			mockCreateGlobalTaskListenerEndpoint({
				schema: z.object({id: z.literal('my-listener'), type: z.literal('my-type')}),
				successResponse: HttpResponse.json({}),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		const screen = await render(<AddGlobalTaskListenerModal isOpen onClose={mockOnClose} />, {wrapper: getWrapper()});

		await userEvent.fill(screen.getByRole('textbox', {name: 'Listener ID'}), 'my-listener');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Listener type'}), 'my-type');
		await userEvent.click(screen.getByRole('combobox', {name: 'Event types'}));
		await userEvent.click(screen.getByRole('option', {name: 'Creating'}));
		await userEvent.keyboard('{Escape}');
		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await vi.waitFor(() => expect(mockOnClose).toHaveBeenCalledOnce());
	});

	it('should show validation errors when submitting with empty fields', async () => {
		const screen = await render(<AddGlobalTaskListenerModal isOpen onClose={() => {}} />, {wrapper: getWrapper()});

		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await expect.element(screen.getByRole('textbox', {name: 'Listener ID'})).toHaveAttribute('aria-invalid', 'true');
		await expect.element(screen.getByRole('textbox', {name: 'Listener type'})).toHaveAttribute('aria-invalid', 'true');
		await expect.element(screen.getByText('At least one event type is required')).toBeVisible();
	});

	it('should show a validation error for an invalid listener type', async () => {
		const screen = await render(<AddGlobalTaskListenerModal isOpen onClose={() => {}} />, {wrapper: getWrapper()});

		await userEvent.fill(screen.getByRole('textbox', {name: 'Listener ID'}), 'my-listener');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Listener type'}), 'invalid type!');
		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await expect
			.element(
				screen.getByText('Listener type must be 256 characters or fewer, using only letters, numbers, and _ ~ @ . + -'),
			)
			.toBeVisible();
	});

	it('should show a validation error for an invalid listener ID', async () => {
		const screen = await render(<AddGlobalTaskListenerModal isOpen onClose={() => {}} />, {wrapper: getWrapper()});

		await userEvent.fill(screen.getByRole('textbox', {name: 'Listener ID'}), 'invalid id!');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Listener type'}), 'my-type');
		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await expect
			.element(
				screen.getByText('Listener ID must be 256 characters or fewer, using only letters, numbers, and _ ~ @ . + -'),
			)
			.toBeVisible();
	});

	it('should show a generic error toast when the create request fails', async ({worker}) => {
		worker.use(
			mockCreateGlobalTaskListenerEndpoint({
				schema: z.object({id: z.literal('will-not-match')}),
				successResponse: HttpResponse.json({}),
				failureResponse: new HttpResponse(null, {status: 500}),
			}),
		);

		const screen = await render(<AddGlobalTaskListenerModal isOpen onClose={() => {}} />, {wrapper: getWrapper()});

		await userEvent.fill(screen.getByRole('textbox', {name: 'Listener ID'}), 'my-listener');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Listener type'}), 'my-type');
		await userEvent.click(screen.getByRole('combobox', {name: 'Event types'}));
		await userEvent.click(screen.getByRole('option', {name: 'Creating'}));
		await userEvent.keyboard('{Escape}');
		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await expect.element(screen.getByText('Failed to create global task listener')).toBeVisible();
	});

	it('should call onClose when cancelled', async () => {
		const mockOnClose = vi.fn();
		const screen = await render(<AddGlobalTaskListenerModal isOpen onClose={mockOnClose} />, {wrapper: getWrapper()});

		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}).last());

		expect(mockOnClose).toHaveBeenCalledOnce();
	});
});
