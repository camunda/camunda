/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {HttpResponse} from 'msw';
import {Form} from 'react-final-form';
import {render} from 'vitest-browser-react';
import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {mockCurrentUserEndpoint} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {TenantField} from './TenantField';

function getWrapper(initialValues?: {tenantId?: string}) {
	const queryClient = new QueryClient({
		defaultOptions: {
			queries: {retry: false},
		},
	});

	const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
		<QueryClientProvider client={queryClient}>
			<Form onSubmit={() => {}} initialValues={initialValues}>
				{({handleSubmit}) => <form onSubmit={handleSubmit}>{children}</form>}
			</Form>
		</QueryClientProvider>
	);

	return Wrapper;
}

describe('<TenantField />', () => {
	it('should only contain all tenants filter', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(createCurrentUser({tenants: []})),
			}),
		);

		const screen = await render(<TenantField />, {wrapper: getWrapper()});

		await screen.getByRole('combobox', {name: 'Select a tenant'}).click();

		await expect.element(screen.getByRole('option', {name: 'All tenants'})).toBeVisible();
		await expect.poll(() => screen.getByRole('option').elements().length).toBe(1);
	});

	it('should contain list of tenants', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(
					createCurrentUser({
						tenants: [
							{tenantId: '<default>', name: 'Default Tenant', description: null},
							{tenantId: 'tenant-A', name: 'Tenant A', description: null},
							{tenantId: 'tenant-B', name: 'Tenant B', description: null},
						],
					}),
				),
			}),
		);

		const screen = await render(<TenantField />, {wrapper: getWrapper()});

		await screen.getByRole('combobox', {name: 'Select a tenant'}).click();

		await expect.element(screen.getByRole('option', {name: 'All tenants'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: 'Default Tenant'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: 'Tenant A'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: 'Tenant B'})).toBeVisible();
	});

	it('should not set value if it is not valid', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(
					createCurrentUser({
						tenants: [{tenantId: '<default>', name: 'Default Tenant', description: null}],
					}),
				),
			}),
		);

		const screen = await render(<TenantField />, {wrapper: getWrapper({tenantId: 'invalid-tenant'})});

		await expect.element(screen.getByRole('combobox', {name: 'Select a tenant'})).toHaveTextContent('Select a tenant');
	});
});
