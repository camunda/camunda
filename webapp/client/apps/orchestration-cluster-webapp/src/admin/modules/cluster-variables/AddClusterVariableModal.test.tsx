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
	mockCreateGlobalClusterVariableEndpoint,
	mockCreateTenantClusterVariableEndpoint,
	mockGetGlobalClusterVariableEndpoint,
	mockGetTenantClusterVariableEndpoint,
	mockQueryTenantsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createClusterVariable} from '#/shared-test-modules/api-mocks/cluster-variables';
import {createQueryTenantsResponse, createTenant} from '#/shared-test-modules/api-mocks/tenants';
import {AddClusterVariableModal} from './AddClusterVariableModal';

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

async function renderModal(props: Partial<React.ComponentProps<typeof AddClusterVariableModal>> = {}) {
	const onClose = vi.fn();
	const screen = await render(<AddClusterVariableModal isOpen isTenantScopeAvailable onClose={onClose} {...props} />, {
		wrapper: getWrapper(),
	});
	await expect
		.element(screen.getByRole('textbox', {name: 'Value - Enter string or JSON'}), MONACO_LOAD)
		.toBeInTheDocument();

	return {screen, onClose};
}

describe('<AddClusterVariableModal />', () => {
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

	it('should render nothing while closed', async () => {
		const screen = await render(<AddClusterVariableModal isOpen={false} isTenantScopeAvailable onClose={() => {}} />, {
			wrapper: getWrapper(),
		});

		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
	});

	it('should create a global cluster variable with the entered JSON value', async ({worker}) => {
		worker.use(
			mockCreateGlobalClusterVariableEndpoint({
				schema: z.object({name: z.literal('my-variable'), value: z.object({answer: z.literal(42)})}),
				successResponse: HttpResponse.json(createClusterVariable({name: 'my-variable', value: '{"answer":42}'})),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockGetGlobalClusterVariableEndpoint({
				successResponse: HttpResponse.json(createClusterVariable({name: 'my-variable', value: '{"answer":42}'})),
			}),
		);
		const {screen, onClose} = await renderModal();

		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), '  my-variable ');
		await replaceMonacoValue(screen.getByRole('textbox', {name: 'Value - Enter string or JSON'}), '{"answer": 42}');
		await userEvent.click(screen.getByRole('button', {name: 'Create'}));

		await vi.waitFor(() => expect(onClose).toHaveBeenCalledOnce());
	});

	it('should create a tenant-scoped cluster variable for the selected tenant', async ({worker}) => {
		worker.use(
			mockQueryTenantsEndpoint({
				successResponse: HttpResponse.json(
					createQueryTenantsResponse([createTenant({tenantId: 'tenant-a', name: 'Tenant A'})]),
				),
			}),
			mockCreateTenantClusterVariableEndpoint({
				schema: z.object({name: z.literal('my-variable'), value: z.literal('hello')}),
				successResponse: HttpResponse.json(
					createClusterVariable({name: 'my-variable', scope: 'TENANT', tenantId: 'tenant-a', value: '"hello"'}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockGetTenantClusterVariableEndpoint({
				successResponse: HttpResponse.json(
					createClusterVariable({name: 'my-variable', scope: 'TENANT', tenantId: 'tenant-a', value: '"hello"'}),
				),
			}),
		);
		const {screen, onClose} = await renderModal();

		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'my-variable');
		// Typed before the select opens: Radix returns focus to the select trigger as it closes, swallowing keystrokes.
		await replaceMonacoValue(screen.getByRole('textbox', {name: 'Value - Enter string or JSON'}), '"hello"');
		await userEvent.click(screen.getByRole('radio', {name: 'Tenant'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Tenant'}));
		await userEvent.click(screen.getByRole('option', {name: 'Tenant A'}));
		await expect.element(screen.getByRole('combobox', {name: 'Tenant'})).toHaveTextContent('Tenant A');
		await userEvent.click(screen.getByRole('button', {name: 'Create'}));

		await vi.waitFor(() => expect(onClose).toHaveBeenCalledOnce());
	});

	it('should disable the tenant scope when it is not available', async () => {
		const {screen} = await renderModal({isTenantScopeAvailable: false});

		await expect.element(screen.getByRole('radio', {name: 'Tenant'})).toBeDisabled();
		await expect.element(screen.getByRole('radio', {name: 'Global'})).toBeChecked();
	});

	it('should require a tenant for a tenant-scoped variable', async ({worker}) => {
		worker.use(
			mockQueryTenantsEndpoint({successResponse: HttpResponse.json(createQueryTenantsResponse([createTenant()]))}),
		);
		const {screen} = await renderModal();

		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'my-variable');
		await userEvent.click(screen.getByRole('radio', {name: 'Tenant'}));
		await replaceMonacoValue(screen.getByRole('textbox', {name: 'Value - Enter string or JSON'}), '1');
		await userEvent.click(screen.getByRole('button', {name: 'Create'}));

		await expect.element(screen.getByText('Tenant is required')).toBeVisible();
	});

	it('should show validation errors when submitting without a name and value', async () => {
		const {screen} = await renderModal();

		await userEvent.click(screen.getByRole('button', {name: 'Create'}));

		await expect.element(screen.getByRole('textbox', {name: 'Name'})).toHaveAttribute('aria-invalid', 'true');
		await expect.element(screen.getByText('Name is required')).toBeVisible();
		await expect.element(screen.getByText('Value is required')).toBeVisible();
	});

	it('should reject a name with illegal characters', async () => {
		const {screen} = await renderModal();

		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'my variable!');
		await replaceMonacoValue(screen.getByRole('textbox', {name: 'Value - Enter string or JSON'}), '1');
		await userEvent.click(screen.getByRole('button', {name: 'Create'}));

		await expect
			.element(
				screen.getByText(
					'Name can only contain letters, numbers, and the characters _ ~ @ . + -, and must be at most 256 characters',
				),
			)
			.toBeVisible();
	});

	it('should reject a name longer than 256 characters', async () => {
		const {screen} = await renderModal();

		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'a'.repeat(257));
		await replaceMonacoValue(screen.getByRole('textbox', {name: 'Value - Enter string or JSON'}), '1');
		await userEvent.click(screen.getByRole('button', {name: 'Create'}));

		await expect.element(screen.getByText(/must be at most 256 characters/)).toBeVisible();
	});

	it('should accept every character allowed in a name', async ({worker}) => {
		worker.use(
			mockCreateGlobalClusterVariableEndpoint({
				schema: z.object({name: z.literal('aZ09_~@.+-')}),
				successResponse: HttpResponse.json(createClusterVariable({name: 'aZ09_~@.+-', value: '1'})),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockGetGlobalClusterVariableEndpoint({
				successResponse: HttpResponse.json(createClusterVariable({name: 'aZ09_~@.+-', value: '1'})),
			}),
		);
		const {screen, onClose} = await renderModal();

		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'aZ09_~@.+-');
		await replaceMonacoValue(screen.getByRole('textbox', {name: 'Value - Enter string or JSON'}), '1');
		await userEvent.click(screen.getByRole('button', {name: 'Create'}));

		await vi.waitFor(() => expect(onClose).toHaveBeenCalledOnce());
	});

	it('should reject a value that is not valid JSON', async () => {
		const {screen} = await renderModal();

		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'my-variable');
		await replaceMonacoValue(screen.getByRole('textbox', {name: 'Value - Enter string or JSON'}), 'nope');
		await userEvent.click(screen.getByRole('button', {name: 'Create'}));

		await expect.element(screen.getByText('Value is invalid. It must be a string or valid JSON.')).toBeVisible();
	});

	it('should reject a null value', async () => {
		const {screen} = await renderModal();

		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'my-variable');
		await replaceMonacoValue(screen.getByRole('textbox', {name: 'Value - Enter string or JSON'}), 'null');
		await userEvent.click(screen.getByRole('button', {name: 'Create'}));

		await expect.element(screen.getByText('Value cannot be null')).toBeVisible();
	});

	it('should show an inline error when the name already exists in the scope', async ({worker}) => {
		worker.use(
			mockCreateGlobalClusterVariableEndpoint({
				successResponse: HttpResponse.json(
					{
						type: 'about:blank',
						title: 'ALREADY_EXISTS',
						status: 409,
						detail:
							"Expected to create cluster variable with name 'my-variable', but a variable with this name already exists in the scope.",
						instance: '/v2/cluster-variables/global',
					},
					{status: 409},
				),
			}),
		);
		const {screen} = await renderModal();

		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'my-variable');
		await replaceMonacoValue(screen.getByRole('textbox', {name: 'Value - Enter string or JSON'}), '1');
		await userEvent.click(screen.getByRole('button', {name: 'Create'}));

		await expect
			.element(screen.getByText('A cluster variable with this name already exists in this scope'))
			.toBeVisible();
	});

	it('should show an error toast when the creation fails', async ({worker}) => {
		worker.use(mockCreateGlobalClusterVariableEndpoint({successResponse: new HttpResponse(null, {status: 500})}));
		const {screen, onClose} = await renderModal();

		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'my-variable');
		await replaceMonacoValue(screen.getByRole('textbox', {name: 'Value - Enter string or JSON'}), '1');
		await userEvent.click(screen.getByRole('button', {name: 'Create'}));

		await expect.element(screen.getByText('Failed to create cluster variable')).toBeVisible();
		expect(onClose).not.toHaveBeenCalled();
	});

	it('should call onClose when cancelled', async () => {
		const {screen, onClose} = await renderModal();

		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}));

		expect(onClose).toHaveBeenCalledOnce();
	});
});
