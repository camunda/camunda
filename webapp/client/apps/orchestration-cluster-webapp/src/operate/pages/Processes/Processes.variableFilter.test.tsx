/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {render} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {TooltipProvider} from '@camunda/design-system';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {
	Outlet,
	RouterProvider,
	createMemoryHistory,
	createRootRouteWithContext,
	createRoute,
	createRouter,
} from '@tanstack/react-router';
import {it} from '#/vitest-modules/test-extend';
import {
	mockQueryBatchOperationItemsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createQueryProcessDefinitionsResponse} from '#/shared-test-modules/api-mocks/process-definitions';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {ProcessesHarness} from './ProcessesHarness';
import {VariableFilterModal} from './VariablesFilter/VariableFilterModal';

const STORAGE_KEY = 'operate.variableFilter.conditions';
const PROCESS_DEFINITIONS = HttpResponse.json(createQueryProcessDefinitionsResponse());
const MATCHING_INSTANCES = HttpResponse.json(
	createQueryProcessInstancesResponse({items: [createProcessInstance({processInstanceKey: '1'})]}),
);
const NO_INSTANCES = HttpResponse.json(createQueryProcessInstancesResponse());

function mockInstancesMatching(filter: Record<string, unknown>) {
	return [
		mockQueryBatchOperationItemsEndpoint({successResponse: HttpResponse.json(createPaginatedResponse())}),
		mockQueryProcessInstancesEndpoint({
			schema: z.object({
				filter: z.object(
					Object.fromEntries(
						Object.entries(filter).map(([key, value]) => [
							key,
							value === undefined
								? z.undefined().optional()
								: z.unknown().refine((actual) => JSON.stringify(actual) === JSON.stringify(value)),
						]),
					),
				),
			}),
			successResponse: MATCHING_INSTANCES,
			failureResponse: NO_INSTANCES,
		}),
	];
}

function storeConditions(conditions: unknown[]) {
	sessionStorage.setItem(STORAGE_KEY, JSON.stringify(conditions));
}

function getStoredConditions(): unknown {
	return JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? 'null');
}

async function renderProcessesPage(initialEntry = '/operate/processes', previousEntries: string[] = []) {
	const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
	const rootRoute = createRootRouteWithContext<{queryClient: QueryClient}>()({component: () => <Outlet />});
	const processesRoute = createRoute({
		getParentRoute: () => rootRoute,
		path: '/operate/processes',
		component: () => (
			<>
				<ProcessesHarness />
				<Outlet />
			</>
		),
	});
	const router = createRouter({
		routeTree: rootRoute.addChildren([
			processesRoute.addChildren([
				createRoute({getParentRoute: () => processesRoute, path: '/'}),
				createRoute({getParentRoute: () => processesRoute, path: '/filters/variables', component: VariableFilterModal}),
			]),
		]),
		history: createMemoryHistory({initialEntries: [...previousEntries, initialEntry]}),
		defaultPendingMinMs: 0,
		context: {queryClient},
	});
	await router.load();

	const screen = await render(
		<TooltipProvider>
			<QueryClientProvider client={queryClient}>
				<RouterProvider router={router} />
			</QueryClientProvider>
		</TooltipProvider>,
	);

	return {...screen, router};
}

describe('Processes variable filter', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should filter instances by an inline equals condition once name and value are filled', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			...mockInstancesMatching({variables: [{name: 'status', value: {$eq: '"open"'}}]}),
		);
		const screen = await renderProcessesPage();

		await userEvent.click(screen.getByRole('button', {name: 'More Filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Variables'}));
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'status');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Value'}), '"open"');

		await expect.element(screen.getByRole('link', {name: 'View instance 1'})).toBeVisible();
		expect(getStoredConditions()).toEqual([{name: 'status', operator: 'equals', value: '"open"'}]);
		expect(screen.router.state.location.search).toEqual({});
	});

	it.for([
		{field: 'Name', error: 'Name has to be filled'},
		{field: 'Value', error: 'Value has to be filled'},
	] as const)(
		'should keep the applied condition when the inline $field is cleared',
		async ({field, error}, {worker}) => {
			storeConditions([{name: 'status', operator: 'equals', value: '"open"'}]);
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
				...mockInstancesMatching({variables: [{name: 'status', value: {$eq: '"open"'}}]}),
			);
			const screen = await renderProcessesPage();

			await expect.element(screen.getByRole('textbox', {name: 'Name'})).toHaveValue('status');
			await expect.element(screen.getByRole('textbox', {name: 'Value'})).toHaveValue('"open"');
			await userEvent.clear(screen.getByRole('textbox', {name: field}));

			await expect.element(screen.getByText(error)).toBeVisible();
			await expect.element(screen.getByRole('link', {name: 'View instance 1'})).toBeVisible();
			expect(getStoredConditions()).toEqual([{name: 'status', operator: 'equals', value: '"open"'}]);
		},
	);

	it('should show the parse error for an unparseable inline value without applying it', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			...mockInstancesMatching({variables: undefined}),
		);
		const screen = await renderProcessesPage();

		await userEvent.click(screen.getByRole('button', {name: 'More Filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Variables'}));
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'status');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Value'}), '"NEW');

		await expect.element(screen.getByText('Invalid value: "NEW')).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'View instance 1'})).toBeVisible();
		expect(getStoredConditions()).toBeNull();
	});

	it('should clear the condition when both inline fields are emptied', async ({worker}) => {
		storeConditions([{name: 'status', operator: 'equals', value: '"open"'}]);
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			...mockInstancesMatching({variables: undefined}),
		);
		const screen = await renderProcessesPage();

		await userEvent.clear(screen.getByRole('textbox', {name: 'Name'}));
		await userEvent.clear(screen.getByRole('textbox', {name: 'Value'}));

		await expect.element(screen.getByRole('link', {name: 'View instance 1'})).toBeVisible();
		expect(getStoredConditions()).toBeNull();
		await expect.element(screen.getByText('Name has to be filled')).not.toBeInTheDocument();
		await expect.element(screen.getByText('Value has to be filled')).not.toBeInTheDocument();
	});

	it('should restore conditions from the session and keep only valid stored entries', async ({worker}) => {
		storeConditions([
			{name: 'status', operator: 'equals', value: '"open"'},
			{name: '', operator: 'equals', value: '1'},
			{name: 'region', operator: 'unknown', value: 'eu'},
			{name: 'retries', operator: 'exists', value: ''},
		]);
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			...mockInstancesMatching({
				variables: [
					{name: 'status', value: {$eq: '"open"'}},
					{name: 'retries', value: {$exists: true}},
				],
			}),
		);
		const screen = await renderProcessesPage();

		const conditionList = screen.getByRole('list', {name: 'Active variable filters'});
		await expect.element(conditionList).toBeVisible();
		await expect.element(conditionList.getByRole('listitem').nth(0)).toHaveTextContent('status equals "open"');
		await expect.element(conditionList.getByRole('listitem').nth(1)).toHaveTextContent('retries exists');
		await expect.element(conditionList.getByRole('listitem').nth(2)).not.toBeInTheDocument();
		await expect.element(screen.getByRole('link', {name: 'View instance 1'})).toBeVisible();
	});

	it('should truncate long condition values in the condition list', async ({worker}) => {
		storeConditions([
			{name: 'payload', operator: 'contains', value: 'x'.repeat(60)},
			{name: 'retries', operator: 'exists', value: ''},
		]);
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryProcessInstancesEndpoint({successResponse: NO_INSTANCES}),
		);
		const screen = await renderProcessesPage();

		await expect
			.element(screen.getByRole('list', {name: 'Active variable filters'}).getByRole('listitem').first())
			.toHaveTextContent(`payload contains ${'x'.repeat(47)}...`);
	});

	it('should keep the inline condition when another filter is submitted', async ({worker}) => {
		storeConditions([{name: 'status', operator: 'equals', value: '"open"'}]);
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			...mockInstancesMatching({
				variables: [{name: 'status', value: {$eq: '"open"'}}],
				errorMessage: {$in: ['timeout']},
			}),
		);
		const screen = await renderProcessesPage();

		await userEvent.click(screen.getByRole('button', {name: 'More Filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Error Message'}));
		await userEvent.fill(screen.getByRole('textbox', {name: 'Error Message'}), 'timeout');

		await expect.poll(() => screen.router.state.location.search).toEqual({errorMessage: 'timeout'});
		await expect.element(screen.getByRole('link', {name: 'View instance 1'})).toBeVisible();
		await expect.element(screen.getByRole('textbox', {name: 'Name'})).toHaveValue('status');
		await expect.element(screen.getByRole('textbox', {name: 'Value'})).toHaveValue('"open"');
	});

	it('should keep an unsubmitted filter edit when the inline condition changes', async ({worker}) => {
		storeConditions([{name: 'status', operator: 'equals', value: '"open"'}]);
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			...mockInstancesMatching({
				variables: [{name: 'status', value: {$eq: '"open2"'}}],
				errorMessage: {$in: ['timeout']},
			}),
		);
		const screen = await renderProcessesPage();

		await userEvent.click(screen.getByRole('button', {name: 'More Filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Error Message'}));
		await userEvent.fill(screen.getByRole('textbox', {name: 'Error Message'}), 'timeout');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Value'}), '"open2"');

		await expect.element(screen.getByRole('textbox', {name: 'Error Message'})).toHaveValue('timeout');
		await expect.poll(() => screen.router.state.location.search).toEqual({errorMessage: 'timeout'});
		await expect.element(screen.getByRole('link', {name: 'View instance 1'})).toBeVisible();
	});

	it('should open the modal route with the current search and an incomplete inline draft', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryProcessInstancesEndpoint({successResponse: NO_INSTANCES}),
		);
		const screen = await renderProcessesPage('/operate/processes?errorMessage=timeout');

		await userEvent.click(screen.getByRole('button', {name: 'More Filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Variables'}));
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'status');
		await userEvent.click(screen.getByRole('button', {name: 'Add condition'}));

		const dialog = screen.getByRole('dialog', {name: 'Filter by variable'});
		await expect.element(dialog.getByRole('textbox', {name: 'Name'})).toHaveValue('status');
		expect(screen.router.state.location.pathname).toBe('/operate/processes/filters/variables');
		expect(screen.router.state.location.search).toEqual({errorMessage: 'timeout'});
		expect(getStoredConditions()).toBeNull();
	});

	it('should apply modal conditions and list them in the filters panel', async ({worker}) => {
		storeConditions([{name: 'status', operator: 'equals', value: '"open"'}]);
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			...mockInstancesMatching({
				variables: [
					{name: 'status', value: {$eq: '"open"'}},
					{name: 'region', value: {$exists: true}},
				],
			}),
		);
		const screen = await renderProcessesPage();

		await userEvent.click(screen.getByRole('button', {name: 'Add condition'}));
		const dialog = screen.getByRole('dialog', {name: 'Filter by variable'});
		await userEvent.click(dialog.getByRole('button', {name: 'Add condition'}));
		await userEvent.fill(dialog.getByRole('textbox', {name: 'Name'}).last(), 'region');
		await userEvent.click(dialog.getByRole('combobox', {name: 'Operator'}).last());
		await userEvent.click(screen.getByRole('option', {name: 'exists'}));
		await userEvent.click(dialog.getByRole('button', {name: 'Apply'}));

		await expect.element(dialog).not.toBeInTheDocument();
		expect(screen.router.state.location.pathname).toBe('/operate/processes');
		const conditionList = screen.getByRole('list', {name: 'Active variable filters'});
		await expect.element(conditionList.getByRole('listitem').nth(0)).toHaveTextContent('status equals "open"');
		await expect.element(conditionList.getByRole('listitem').nth(1)).toHaveTextContent('region exists');
		await expect.element(screen.getByRole('link', {name: 'View instance 1'})).toBeVisible();
	});

	it('should show a single condition applied from the modal in the inline fields', async ({worker}) => {
		storeConditions([
			{name: 'status', operator: 'equals', value: '"open"'},
			{name: 'region', operator: 'equals', value: '"eu"'},
		]);
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			...mockInstancesMatching({variables: [{name: 'region', value: {$eq: '"eu"'}}]}),
		);
		const screen = await renderProcessesPage();

		await userEvent.click(screen.getByRole('button', {name: 'Edit conditions'}));
		const dialog = screen.getByRole('dialog', {name: 'Filter by variable'});
		await userEvent.click(dialog.getByRole('button', {name: 'Remove condition'}).first());
		await userEvent.click(dialog.getByRole('button', {name: 'Apply'}));

		await expect.element(screen.getByRole('textbox', {name: 'Name'})).toHaveValue('region');
		await expect.element(screen.getByRole('textbox', {name: 'Value'})).toHaveValue('"eu"');
		await expect.element(screen.getByRole('link', {name: 'View instance 1'})).toBeVisible();
		expect(getStoredConditions()).toEqual([{name: 'region', operator: 'equals', value: '"eu"'}]);
	});

	it('should close the modal on browser back without changing the conditions', async ({worker}) => {
		storeConditions([{name: 'status', operator: 'equals', value: '"open"'}]);
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryProcessInstancesEndpoint({successResponse: NO_INSTANCES}),
		);
		const screen = await renderProcessesPage();

		await userEvent.click(screen.getByRole('button', {name: 'Add condition'}));
		const dialog = screen.getByRole('dialog', {name: 'Filter by variable'});
		await userEvent.fill(dialog.getByRole('textbox', {name: 'Name'}), 'region');
		screen.router.history.back();

		await expect.element(dialog).not.toBeInTheDocument();
		expect(screen.router.state.location.pathname).toBe('/operate/processes');
		await expect.element(screen.getByRole('textbox', {name: 'Name'})).toHaveValue('status');
		expect(getStoredConditions()).toEqual([{name: 'status', operator: 'equals', value: '"open"'}]);
	});

	it('should drop the modal from history when it is canceled after opening it from the list', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryProcessInstancesEndpoint({successResponse: NO_INSTANCES}),
		);
		const screen = await renderProcessesPage('/operate/processes?errorMessage=timeout', ['/operate/previous']);

		await userEvent.click(screen.getByRole('button', {name: 'More Filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Variables'}));
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'status');
		await userEvent.click(screen.getByRole('button', {name: 'Add condition'}));
		const dialog = screen.getByRole('dialog', {name: 'Filter by variable'});
		await userEvent.click(dialog.getByRole('button', {name: 'Cancel'}));

		await expect.element(dialog).not.toBeInTheDocument();
		expect(screen.router.state.location.pathname).toBe('/operate/processes');
		expect(screen.router.state.location.search).toEqual({errorMessage: 'timeout'});

		screen.router.history.back();

		await expect.poll(() => screen.router.state.location.pathname).toBe('/operate/previous');
		await expect.element(dialog).not.toBeInTheDocument();
	});

	it('should drop the modal from history when it is applied after opening it from the list', async ({worker}) => {
		storeConditions([
			{name: 'status', operator: 'equals', value: '"open"'},
			{name: 'region', operator: 'exists', value: ''},
		]);
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryProcessInstancesEndpoint({successResponse: NO_INSTANCES}),
		);
		const screen = await renderProcessesPage('/operate/processes', ['/operate/previous']);

		await userEvent.click(screen.getByRole('button', {name: 'Edit conditions'}));
		const dialog = screen.getByRole('dialog', {name: 'Filter by variable'});
		await userEvent.click(dialog.getByRole('button', {name: 'Remove condition'}).first());
		await userEvent.click(dialog.getByRole('button', {name: 'Apply'}));

		await expect.element(dialog).not.toBeInTheDocument();
		expect(screen.router.state.location.pathname).toBe('/operate/processes');
		expect(getStoredConditions()).toEqual([{name: 'region', operator: 'exists', value: ''}]);

		screen.router.history.back();

		await expect.poll(() => screen.router.state.location.pathname).toBe('/operate/previous');
		await expect.element(dialog).not.toBeInTheDocument();
	});

	it('should show the Variables filter and the modal when opening the modal URL directly', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryProcessInstancesEndpoint({successResponse: NO_INSTANCES}),
		);
		const screen = await renderProcessesPage('/operate/processes/filters/variables', ['/operate/previous']);

		await expect.element(screen.getByRole('dialog', {name: 'Filter by variable'})).toBeVisible();
		await expect.element(screen.getByRole('heading', {name: 'Variables'})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}));

		await expect.element(screen.getByRole('dialog', {name: 'Filter by variable'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('heading', {name: 'Variables'})).toBeVisible();
		expect(screen.router.state.location.pathname).toBe('/operate/processes');

		screen.router.history.back();

		await expect.poll(() => screen.router.state.location.pathname).toBe('/operate/previous');
		await expect.element(screen.getByRole('dialog', {name: 'Filter by variable'})).not.toBeInTheDocument();
	});

	it('should clear the conditions and submit pending filter edits when the Variables filter is removed', async ({
		worker,
	}) => {
		storeConditions([
			{name: 'status', operator: 'equals', value: '"open"'},
			{name: 'region', operator: 'exists', value: ''},
		]);
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			...mockInstancesMatching({variables: undefined, errorMessage: {$in: ['timeout']}}),
		);
		const screen = await renderProcessesPage();

		await userEvent.click(screen.getByRole('button', {name: 'More Filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Error Message'}));
		await userEvent.type(screen.getByRole('textbox', {name: 'Error Message'}), 'timeout');
		await userEvent.hover(screen.getByRole('list', {name: 'Active variable filters'}));
		await userEvent.click(screen.getByRole('button', {name: 'Remove Variables Filter'}));

		await expect.poll(() => screen.router.state.location.search, {timeout: 400}).toEqual({errorMessage: 'timeout'});
		expect(getStoredConditions()).toBeNull();
		await expect.element(screen.getByRole('heading', {name: 'Variables'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('link', {name: 'View instance 1'})).toBeVisible();
	});

	it('should clear the conditions on Reset filters', async ({worker}) => {
		storeConditions([{name: 'status', operator: 'equals', value: '"open"'}]);
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			...mockInstancesMatching({variables: undefined}),
		);
		const screen = await renderProcessesPage();

		await expect.element(screen.getByRole('textbox', {name: 'Name'})).toHaveValue('status');
		await userEvent.click(screen.getByRole('button', {name: 'Reset filters'}));

		await expect.element(screen.getByRole('heading', {name: 'Variables'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('link', {name: 'View instance 1'})).toBeVisible();
		expect(getStoredConditions()).toBeNull();
		await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeDisabled();

		await userEvent.click(screen.getByRole('button', {name: 'More Filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Variables'}));

		await expect.element(screen.getByRole('textbox', {name: 'Name'})).toHaveValue('');
		await expect.element(screen.getByRole('textbox', {name: 'Value'})).toHaveValue('');
		expect(getStoredConditions()).toBeNull();
	});
});
