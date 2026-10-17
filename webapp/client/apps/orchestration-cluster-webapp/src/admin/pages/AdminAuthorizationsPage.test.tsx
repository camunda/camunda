/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import type {Authorization} from '@camunda/camunda-api-zod-schemas/8.11';
import {cleanup, render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {TooltipProvider, Toaster, toast} from '@camunda/design-system';
import {afterEach, describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {
	mockCreateAuthorizationEndpoint,
	mockDeleteAuthorizationEndpoint,
	mockGetAuthorizationEndpoint,
	mockQueryAuthorizationsEndpoint,
	mockQueryGroupsEndpoint,
	mockQueryRolesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createAuthorization, createQueryAuthorizationsResponse} from '#/shared-test-modules/api-mocks/authorizations';
import {getAuthorizationsRequestBody} from '#/admin/modules/authorizations/getAuthorizationsRequestBody';
import {queries} from '#/shared/http/queries';
import {AdminAuthorizationsPage, type AdminAuthorizationsPageProps} from './AdminAuthorizationsPage';

const DEBOUNCED = {timeout: 3000};
const RETRY_BUDGET = {timeout: 10_000};

const clientConfig: AdminAuthorizationsPageProps['clientConfig'] = {
	idPattern: null,
	resourcePermissions: {
		PROCESS_DEFINITION: ['READ_PROCESS_DEFINITION', 'READ_PROCESS_INSTANCE'],
		USER_TASK: ['READ', 'COMPLETE'],
		SECRET: ['READ'],
	},
	defaultRoleIds: ['admin'],
};

function getWrapper(queryClient: QueryClient) {
	const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
		<QueryClientProvider client={queryClient}>
			<TooltipProvider>{children}</TooltipProvider>
			<Toaster />
		</QueryClientProvider>
	);

	return Wrapper;
}

async function renderPage({
	authorizations = [createAuthorization()],
	totalItems = authorizations.length,
	...overrides
}: Partial<AdminAuthorizationsPageProps> & {authorizations?: Authorization[]; totalItems?: number} = {}) {
	const onSearchChange = vi.fn();
	const props: AdminAuthorizationsPageProps = {
		search: {},
		resourceType: 'PROCESS_DEFINITION',
		availableResourceTypes: ['AUTHORIZATION', 'PROCESS_DEFINITION', 'USER_TASK', 'SECRET'],
		clientConfig,
		isOidc: true,
		isCamundaGroupsEnabled: true,
		onSearchChange,
		...overrides,
	};
	const queryClient = new QueryClient({defaultOptions: {queries: {retry: false, staleTime: Infinity}}});
	queryClient.setQueryData(
		queries.queryAuthorizations(getAuthorizationsRequestBody(props.search, props.resourceType)).queryKey,
		createQueryAuthorizationsResponse({items: authorizations, page: {totalItems}}),
	);
	const screen = await render(<AdminAuthorizationsPage {...props} />, {wrapper: getWrapper(queryClient)});

	return {screen, props, onSearchChange, queryClient};
}

describe('<AdminAuthorizationsPage />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should list authorizations with their resource ID and permissions', async () => {
		const {screen} = await renderPage({
			authorizations: [
				createAuthorization({
					ownerId: 'john.doe',
					ownerType: 'USER',
					resourceId: 'order-process',
					permissionTypes: ['READ_PROCESS_DEFINITION', 'READ_PROCESS_INSTANCE'],
				}),
			],
		});

		await expect.element(screen.getByRole('cell', {name: 'john.doe'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'order-process'})).toBeVisible();
		await expect
			.element(screen.getByRole('cell', {name: 'READ_PROCESS_DEFINITION, READ_PROCESS_INSTANCE'}))
			.toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Resource ID'})).toBeVisible();
	});

	it('should show the resource property name instead of the resource ID for user tasks', async () => {
		const {screen} = await renderPage({
			resourceType: 'USER_TASK',
			authorizations: [
				createAuthorization({
					resourceType: 'USER_TASK',
					resourceId: null,
					resourcePropertyName: 'candidateGroups',
					permissionTypes: ['READ'],
				}),
			],
		});

		await expect.element(screen.getByText('Resource property name')).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'candidateGroups'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Resource ID'})).not.toBeInTheDocument();
	});

	it('should show an empty state when there are no authorizations', async () => {
		const {screen} = await renderPage({authorizations: [], totalItems: 0});

		await expect.element(screen.getByText('No authorizations found.')).toBeVisible();
	});

	it('should return to the first page when the reader picks another resource type', async () => {
		const {screen, onSearchChange} = await renderPage({search: {page: 3}, totalItems: 200});

		await userEvent.click(screen.getByLabelText('Resource type'));
		await userEvent.click(screen.getByRole('option', {name: 'USER_TASK'}));

		expect(onSearchChange).toHaveBeenCalledWith({resourceType: 'USER_TASK', page: undefined});
	});

	it('should keep the toolbar mounted while another resource type loads', async ({worker}) => {
		worker.use(
			mockQueryAuthorizationsEndpoint({
				delay: 500,
				successResponse: HttpResponse.json(
					createQueryAuthorizationsResponse({
						items: [createAuthorization({ownerId: 'jane.doe', resourceType: 'USER_TASK'})],
					}),
				),
			}),
		);
		const {screen, props} = await renderPage({authorizations: [createAuthorization({ownerId: 'john.doe'})]});
		await expect.element(screen.getByRole('cell', {name: 'john.doe'})).toBeVisible();
		const searchInput = screen.getByRole('searchbox').element();

		await screen.rerender(<AdminAuthorizationsPage {...props} resourceType="USER_TASK" />);

		await expect.element(screen.getByTestId('authorizations-skeleton')).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'john.doe'})).not.toBeInTheDocument();
		await expect.element(screen.getByText('No authorizations found.')).not.toBeInTheDocument();
		expect(screen.getByRole('searchbox').element()).toBe(searchInput);
		await expect.element(screen.getByRole('cell', {name: 'jane.doe'})).toBeVisible();
	});

	it('should keep the current rows while the next page loads', async ({worker}) => {
		worker.use(
			mockQueryAuthorizationsEndpoint({
				delay: 500,
				successResponse: HttpResponse.json(
					createQueryAuthorizationsResponse({items: [createAuthorization({ownerId: 'jane.doe'})]}),
				),
			}),
		);
		const {screen, props} = await renderPage({authorizations: [createAuthorization({ownerId: 'john.doe'})]});
		await expect.element(screen.getByRole('cell', {name: 'john.doe'})).toBeVisible();

		await screen.rerender(<AdminAuthorizationsPage {...props} search={{page: 2}} />);

		await expect.element(screen.getByRole('cell', {name: 'john.doe'})).toBeVisible();
		await expect.element(screen.getByTestId('authorizations-skeleton')).not.toBeInTheDocument();
		await expect.element(screen.getByRole('cell', {name: 'jane.doe'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'john.doe'})).not.toBeInTheDocument();
	});

	it('should filter by owner ID once the reader stops typing', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.fill(screen.getByRole('searchbox'), 'demo');

		await vi.waitFor(() => expect(onSearchChange).toHaveBeenCalledWith({ownerId: 'demo', page: undefined}), DEBOUNCED);
	});

	it('should reverse the owner ID order when the reader sorts the column', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Owner ID'}));

		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'ownerId', sortOrder: 'DESC', page: undefined});
	});

	it('should treat the owner ID order as active for user tasks when the URL sorts by resource ID', async () => {
		const {screen, onSearchChange} = await renderPage({
			resourceType: 'USER_TASK',
			search: {sortField: 'resourceId', sortOrder: 'DESC'},
		});

		await userEvent.click(screen.getByRole('button', {name: 'Owner ID'}));

		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'ownerId', sortOrder: 'DESC', page: undefined});
	});

	it('should not offer sorting by permissions', async () => {
		const {screen} = await renderPage();

		await expect.element(screen.getByRole('button', {name: 'Permissions'})).not.toBeInTheDocument();
	});

	it('should disable deleting authorizations of default roles', async () => {
		const {screen} = await renderPage({
			authorizations: [createAuthorization({ownerType: 'ROLE', ownerId: 'admin'})],
		});

		await expect.element(screen.getByRole('button', {name: 'Delete authorization'})).toBeDisabled();
	});

	it('should create an authorization', async ({worker}) => {
		worker.use(
			mockQueryAuthorizationsEndpoint({successResponse: HttpResponse.json(createQueryAuthorizationsResponse())}),
			mockCreateAuthorizationEndpoint({
				schema: z.strictObject({
					ownerType: z.literal('USER'),
					ownerId: z.literal('new.user'),
					resourceType: z.literal('PROCESS_DEFINITION'),
					resourceId: z.literal('order-process'),
					permissionTypes: z.tuple([z.literal('READ_PROCESS_DEFINITION')]),
				}),
				failureResponse: HttpResponse.json({}, {status: 400}),
				successResponse: HttpResponse.json({authorizationKey: '42'}),
			}),
			mockGetAuthorizationEndpoint({successResponse: HttpResponse.json(createAuthorization({authorizationKey: '42'}))}),
		);
		const {screen} = await renderPage({authorizations: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create authorization'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'new.user');
		await userEvent.fill(screen.getByLabelText('Resource ID'), 'order-process');
		await userEvent.click(screen.getByLabelText('READ_PROCESS_DEFINITION'));
		await userEvent.click(screen.getByRole('button', {name: 'Create', exact: true}));

		await expect.element(screen.getByText('Authorization created')).toBeVisible();
	});

	it('should prefix secret names with camunda.secrets. when creating a secret authorization', async ({worker}) => {
		worker.use(
			mockQueryAuthorizationsEndpoint({successResponse: HttpResponse.json(createQueryAuthorizationsResponse())}),
			mockCreateAuthorizationEndpoint({
				schema: z.strictObject({
					ownerType: z.literal('USER'),
					ownerId: z.literal('new.user'),
					resourceType: z.literal('SECRET'),
					resourceId: z.literal('camunda.secrets.db-password'),
					permissionTypes: z.tuple([z.literal('READ')]),
				}),
				failureResponse: HttpResponse.json({}, {status: 400}),
				successResponse: HttpResponse.json({authorizationKey: '43'}),
			}),
			mockGetAuthorizationEndpoint({successResponse: HttpResponse.json(createAuthorization({authorizationKey: '43'}))}),
		);
		const {screen} = await renderPage({resourceType: 'SECRET', authorizations: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create authorization'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'new.user');
		await userEvent.fill(screen.getByLabelText('Resource ID'), 'db-password');
		await userEvent.click(screen.getByLabelText('READ'));
		await userEvent.click(screen.getByRole('button', {name: 'Create', exact: true}));

		await expect.element(screen.getByText('Authorization created')).toBeVisible();
	});

	it('should clear the selected owner when the reader switches between owner types', async ({worker}) => {
		worker.use(
			mockQueryRolesEndpoint({
				successResponse: HttpResponse.json({items: [{roleId: 'admin', name: 'Admin'}], page: {totalItems: 1}}),
			}),
			mockQueryGroupsEndpoint({
				successResponse: HttpResponse.json({items: [{groupId: 'devs', name: 'Devs'}], page: {totalItems: 1}}),
			}),
		);
		const {screen} = await renderPage();
		await userEvent.click(screen.getByRole('button', {name: 'Create authorization'}));
		await userEvent.click(screen.getByLabelText('Owner type'));
		await userEvent.click(screen.getByRole('option', {name: 'ROLE'}));
		await userEvent.click(screen.getByLabelText('Owner', {exact: true}));
		await userEvent.click(screen.getByRole('option', {name: 'Admin (admin)'}));
		await expect.element(screen.getByLabelText('Owner', {exact: true})).toHaveTextContent('Admin (admin)');

		await userEvent.click(screen.getByLabelText('Owner type'));
		await userEvent.click(screen.getByRole('option', {name: 'GROUP'}));

		await expect.element(screen.getByLabelText('Owner', {exact: true})).toHaveTextContent('Select owner');
	});

	it('should send the property name instead of a resource ID for user task authorizations', async ({worker}) => {
		worker.use(
			mockQueryAuthorizationsEndpoint({successResponse: HttpResponse.json(createQueryAuthorizationsResponse())}),
			mockCreateAuthorizationEndpoint({
				schema: z.strictObject({
					ownerType: z.literal('USER'),
					ownerId: z.literal('new.user'),
					resourceType: z.literal('USER_TASK'),
					resourcePropertyName: z.literal('assignee'),
					permissionTypes: z.tuple([z.literal('COMPLETE')]),
				}),
				failureResponse: HttpResponse.json({}, {status: 400}),
				successResponse: HttpResponse.json({authorizationKey: '44'}),
			}),
			mockGetAuthorizationEndpoint({successResponse: HttpResponse.json(createAuthorization({authorizationKey: '44'}))}),
		);
		const {screen} = await renderPage({resourceType: 'USER_TASK', authorizations: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create authorization'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'new.user');
		await userEvent.click(screen.getByLabelText('COMPLETE'));
		await userEvent.click(screen.getByRole('button', {name: 'Create', exact: true}));

		await expect.element(screen.getByText('Authorization created')).toBeVisible();
	});

	it('should require an owner, a resource ID and a permission', async () => {
		const {screen} = await renderPage({authorizations: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create authorization'}));
		await userEvent.click(screen.getByRole('button', {name: 'Create', exact: true}));

		await expect.element(screen.getByText('Owner is required')).toBeVisible();
		await expect.element(screen.getByText('Resource ID is required')).toBeVisible();
		await expect.element(screen.getByText('Select at least one permission', {exact: true}).last()).toBeVisible();
	});

	it('should keep the modal open and show an error when creating fails', async ({worker}) => {
		worker.use(
			mockCreateAuthorizationEndpoint({
				successResponse: HttpResponse.json(
					{type: 'about:blank', title: 'UNAVAILABLE', status: 503, detail: 'Broker unavailable', instance: '/v2'},
					{status: 503},
				),
			}),
		);
		const {screen} = await renderPage({authorizations: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create authorization'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'new.user');
		await userEvent.fill(screen.getByLabelText('Resource ID'), 'order-process');
		await userEvent.click(screen.getByLabelText('READ_PROCESS_DEFINITION'));
		await userEvent.click(screen.getByRole('button', {name: 'Create', exact: true}));

		await expect.element(screen.getByText('Failed to create authorization')).toBeVisible();
		await expect.element(screen.getByRole('dialog')).toBeVisible();
	});

	it('should delete an authorization', async ({worker}) => {
		worker.use(
			mockQueryAuthorizationsEndpoint({successResponse: HttpResponse.json(createQueryAuthorizationsResponse())}),
			mockDeleteAuthorizationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetAuthorizationEndpoint({successResponse: HttpResponse.json({}, {status: 404})}),
		);
		const {screen} = await renderPage({authorizations: [createAuthorization({ownerId: 'john.doe'})]});

		await userEvent.click(screen.getByRole('button', {name: 'Delete authorization'}));
		await userEvent.click(screen.getByRole('button', {name: 'Delete', exact: true}));

		await expect.element(screen.getByText('Authorization deleted')).toBeVisible();
	});

	it('should warn when a created authorization cannot be confirmed on the server', async ({worker}) => {
		worker.use(
			mockCreateAuthorizationEndpoint({successResponse: HttpResponse.json({authorizationKey: '45'})}),
			mockGetAuthorizationEndpoint({successResponse: HttpResponse.json({}, {status: 503})}),
		);
		const {screen} = await renderPage({authorizations: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create authorization'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'new.user');
		await userEvent.fill(screen.getByLabelText('Resource ID'), 'order-process');
		await userEvent.click(screen.getByLabelText('READ_PROCESS_DEFINITION'));
		await userEvent.click(screen.getByRole('button', {name: 'Create', exact: true}));

		await expect.element(screen.getByText('Authorization created')).toBeVisible();
		await expect.element(screen.getByText("Couldn't confirm the change took effect"), RETRY_BUDGET).toBeVisible();
	}, 15_000);

	it('should warn when a deleted authorization is still visible on the server', async ({worker}) => {
		const authorization = createAuthorization({ownerId: 'john.doe'});
		worker.use(
			mockDeleteAuthorizationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetAuthorizationEndpoint({successResponse: HttpResponse.json(authorization)}),
		);
		const {screen} = await renderPage({authorizations: [authorization]});

		await userEvent.click(screen.getByRole('button', {name: 'Delete authorization'}));
		await userEvent.click(screen.getByRole('button', {name: 'Delete', exact: true}));

		await expect.element(screen.getByText('Authorization deleted')).toBeVisible();
		await expect.element(screen.getByText("Couldn't confirm the change took effect"), RETRY_BUDGET).toBeVisible();
	}, 15_000);
});
