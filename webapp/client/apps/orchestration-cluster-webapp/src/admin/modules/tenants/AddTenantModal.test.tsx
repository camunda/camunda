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
import type {SetupWorker} from 'msw/browser';
import {z} from 'zod';
import {cleanup, render} from 'vitest-browser-react';
import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {mockCreateTenantEndpoint, mockQueryTenantsEndpoint} from '#/shared-test-modules/mock-handlers';
import {createTenant} from '#/shared-test-modules/api-mocks/tenants';
import {clearSessionState, storeSessionState} from '#/shared/browser-storage/session-storage';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {AddTenantModal} from './AddTenantModal';

function storeClientConfig({isLoginDelegated}: {isLoginDelegated: boolean}) {
	storeSessionState(
		'clientConfig',
		createSystemConfiguration({
			deployment: {
				isMultiTenancyEnabled: true,
				isTenantsApiEnabled: true,
				isWaitStatesEnabled: true,
				maxRequestSize: 0,
			},
			authentication: {canLogout: true, isLoginDelegated, isCamundaGroupsEnabled: true},
		}),
	);
}

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

async function createTenantViaForm(
	worker: SetupWorker,
	{onClose = vi.fn(), onAssign = vi.fn()}: {onClose?: () => void; onAssign?: () => void} = {},
) {
	const tenant = createTenant({tenantId: 'tenant-a', name: 'Tenant A'});
	worker.use(
		mockCreateTenantEndpoint({successResponse: HttpResponse.json(tenant)}),
		mockQueryTenantsEndpoint({successResponse: HttpResponse.json({items: [tenant], page: {totalItems: 1}})}),
	);
	const screen = await render(<AddTenantModal onClose={onClose} onAssign={onAssign} />, {wrapper: getWrapper()});

	await userEvent.fill(screen.getByRole('textbox', {name: 'Tenant ID'}), 'tenant-a');
	await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'Tenant A');
	await userEvent.click(screen.getByRole('button', {name: 'Create', exact: true}));

	return screen;
}

describe('<AddTenantModal />', () => {
	beforeEach(() => {
		storeClientConfig({isLoginDelegated: false});
	});

	afterEach(async () => {
		await cleanup();
		clearSessionState('clientConfig');
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
				successResponse: HttpResponse.json(
					createTenant({tenantId: 'tenant-a', name: 'Tenant A', description: 'My tenant'}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockQueryTenantsEndpoint({
				successResponse: HttpResponse.json({
					items: [createTenant({tenantId: 'tenant-a', name: 'Tenant A', description: 'My tenant'})],
					page: {totalItems: 1},
				}),
			}),
		);

		const screen = await render(<AddTenantModal onClose={onClose} onAssign={vi.fn()} />, {
			wrapper: getWrapper(),
		});

		await userEvent.fill(screen.getByRole('textbox', {name: 'Tenant ID'}), 'tenant-a');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'Tenant A');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Description'}), 'My tenant');
		await userEvent.click(screen.getByRole('button', {name: 'Create', exact: true}));

		await expect.element(screen.getByText('“Tenant A” was created successfully.')).toBeVisible();
		expect(onClose).not.toHaveBeenCalled();
	});

	it('should offer assigning users, groups and roles after creating a tenant', async ({worker}) => {
		const onClose = vi.fn();
		const onAssign = vi.fn();
		const screen = await createTenantViaForm(worker, {onClose, onAssign});

		await expect.element(screen.getByRole('button', {name: 'Assign user'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Assign group'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Assign role'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Assign mapping rule'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Assign client'})).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('button', {name: 'Assign group'}));

		expect(onClose).toHaveBeenCalledOnce();
		expect(onAssign).toHaveBeenCalledWith('tenant-a', 'groups');
	});

	it('should also offer assigning mapping rules and clients when the login is delegated', async ({worker}) => {
		storeClientConfig({isLoginDelegated: true});

		const screen = await createTenantViaForm(worker);

		await expect.element(screen.getByRole('button', {name: 'Assign mapping rule'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Assign client'})).toBeVisible();
		await expect.element(screen.getByText(/Assign the “connector” role/)).toBeVisible();
	});

	it('should close without navigating when the next step is skipped', async ({worker}) => {
		const onClose = vi.fn();
		const onAssign = vi.fn();
		const screen = await createTenantViaForm(worker, {onClose, onAssign});

		await userEvent.click(screen.getByRole('button', {name: 'Got it'}));

		expect(onClose).toHaveBeenCalledOnce();
		expect(onAssign).not.toHaveBeenCalled();
	});

	it('should show validation errors when submitting with empty fields', async () => {
		const screen = await render(<AddTenantModal onClose={() => {}} onAssign={vi.fn()} />, {
			wrapper: getWrapper(),
		});

		await userEvent.click(screen.getByRole('button', {name: 'Create', exact: true}));

		await expect.element(screen.getByRole('textbox', {name: 'Tenant ID'})).toHaveAttribute('aria-invalid', 'true');
		await expect.element(screen.getByRole('textbox', {name: 'Name'})).toHaveAttribute('aria-invalid', 'true');
	});

	it('should show a validation error for an invalid tenant ID', async () => {
		const screen = await render(<AddTenantModal onClose={() => {}} onAssign={vi.fn()} />, {
			wrapper: getWrapper(),
		});

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

		const screen = await render(<AddTenantModal onClose={onClose} onAssign={vi.fn()} />, {
			wrapper: getWrapper(),
		});

		await userEvent.fill(screen.getByRole('textbox', {name: 'Tenant ID'}), 'tenant-a');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'Tenant A');
		await userEvent.click(screen.getByRole('button', {name: 'Create', exact: true}));

		await expect.element(screen.getByText('Failed to create the tenant')).toBeVisible();
		expect(onClose).not.toHaveBeenCalled();
	});
});
