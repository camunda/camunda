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
import {mockCreateTenantEndpoint} from '#/shared-test-modules/mock-handlers';
import {AddTenantModal} from './AddTenantModal';

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

describe('<AddTenantModal />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should create a tenant with the entered values', async ({worker}) => {
		const onClose = vi.fn();
		worker.use(
			mockCreateTenantEndpoint({
				schema: z.object({
					tenantId: z.literal('tenant-a'),
					name: z.literal('Tenant A'),
					description: z.literal('My tenant'),
				}),
				successResponse: HttpResponse.json({}),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		const screen = await render(<AddTenantModal isOpen onClose={onClose} />, {wrapper: getWrapper()});

		await userEvent.fill(screen.getByRole('textbox', {name: 'Tenant ID'}), 'tenant-a');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'Tenant A');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Description'}), 'My tenant');
		await userEvent.click(screen.getByRole('button', {name: 'Create', exact: true}));

		await vi.waitFor(() => expect(onClose).toHaveBeenCalledOnce());
	});

	it('should show validation errors when submitting with empty fields', async () => {
		const screen = await render(<AddTenantModal isOpen onClose={() => {}} />, {wrapper: getWrapper()});

		await userEvent.click(screen.getByRole('button', {name: 'Create', exact: true}));

		await expect.element(screen.getByRole('textbox', {name: 'Tenant ID'})).toHaveAttribute('aria-invalid', 'true');
		await expect.element(screen.getByRole('textbox', {name: 'Name'})).toHaveAttribute('aria-invalid', 'true');
	});

	it('should show a validation error for an invalid tenant ID', async () => {
		const screen = await render(<AddTenantModal isOpen onClose={() => {}} />, {wrapper: getWrapper()});

		await userEvent.fill(screen.getByRole('textbox', {name: 'Tenant ID'}), 'invalid id!');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'Tenant A');
		await userEvent.click(screen.getByRole('button', {name: 'Create', exact: true}));

		await expect
			.element(screen.getByText('ID must be 1 to 31 characters, using only letters, numbers, and _ . -'))
			.toBeVisible();
	});

	it('should keep the modal open and show an error toast when creation fails', async ({worker}) => {
		const onClose = vi.fn();
		worker.use(mockCreateTenantEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

		const screen = await render(<AddTenantModal isOpen onClose={onClose} />, {wrapper: getWrapper()});

		await userEvent.fill(screen.getByRole('textbox', {name: 'Tenant ID'}), 'tenant-a');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'Tenant A');
		await userEvent.click(screen.getByRole('button', {name: 'Create', exact: true}));

		await expect.element(screen.getByText('Failed to create the tenant')).toBeVisible();
		expect(onClose).not.toHaveBeenCalled();
	});
});
