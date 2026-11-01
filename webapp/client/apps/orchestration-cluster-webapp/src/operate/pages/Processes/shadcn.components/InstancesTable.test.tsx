/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {http, HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockQueryBatchOperationItemsEndpoint,
	mockQueryProcessInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {endpoints} from '@camunda/camunda-api-zod-schemas/8.11';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import type {ProcessesSearch} from '../processesFilter';
import {InstancesTable} from './InstancesTable';

const BASE_SEARCH: ProcessesSearch = {
	active: true,
	incidents: true,
	suspended: true,
	completed: false,
	canceled: false,
};
const PATH = '/operate-preview/processes';

function renderInstancesTable(search: ProcessesSearch = BASE_SEARCH) {
	return renderWithRouter(
		() => (
			<div style={{height: '100vh'}}>
				<InstancesTable search={search} />
			</div>
		),
		{path: PATH, initialEntry: PATH},
	);
}

function mockInstances(items: ReturnType<typeof createProcessInstance>[]) {
	return mockQueryProcessInstancesEndpoint({
		successResponse: HttpResponse.json(createQueryProcessInstancesResponse({items})),
	});
}

describe('<InstancesTable /> (design system)', () => {
	it.beforeEach(({worker}) => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json({items: [], page: {totalItems: 0}}),
			}),
		);
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should render process instance rows with the instance and parent links', async ({worker}) => {
		worker.use(
			mockInstances([
				createProcessInstance({
					processInstanceKey: '2251799813685249',
					processDefinitionName: 'Order process',
					parentProcessInstanceKey: '2251799813685000',
				}),
			]),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByText('Order process')).toBeVisible();
		await expect
			.element(screen.getByRole('link', {name: /View instance 2251799813685249/}))
			.toHaveAttribute('href', '/operate/processes/2251799813685249');
		await expect
			.element(screen.getByRole('link', {name: /View parent instance 2251799813685000/}))
			.toHaveAttribute('href', '/operate/processes/2251799813685000');
	});

	it('should expose the instance state as text for screen readers', async ({worker}) => {
		worker.use(
			mockInstances([
				createProcessInstance({processInstanceKey: '1', processDefinitionName: 'Order process', state: 'ACTIVE'}),
				createProcessInstance({
					processInstanceKey: '2',
					processDefinitionName: 'Shipping process',
					state: 'ACTIVE',
					hasIncident: true,
				}),
			]),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByRole('row', {name: /Order process/}).getByText('Active')).toBeInTheDocument();
		await expect
			.element(screen.getByRole('row', {name: /Shipping process/}).getByText('Incidents'))
			.toBeInTheDocument();
	});

	it('should show the business ID column only when at least one row has one', async ({worker}) => {
		worker.use(mockInstances([createProcessInstance({processInstanceKey: '1', businessId: 'order-1'})]));

		const screen = await renderInstancesTable();

		await expect.element(screen.getByText('order-1')).toBeVisible();
	});

	it('should not show the business ID column when no row has one', async ({worker}) => {
		worker.use(mockInstances([createProcessInstance({processInstanceKey: '1', businessId: undefined})]));

		const screen = await renderInstancesTable();

		await expect.element(screen.getByRole('button', {name: 'Start Date'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Business ID'})).not.toBeInTheDocument();
	});

	it('should show the operation state column when filtered by a batch operation', async ({worker}) => {
		worker.use(
			mockInstances([createProcessInstance({processInstanceKey: '1'})]),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json({
					items: [
						{
							batchOperationKey: 'batch-1',
							itemKey: '1',
							processInstanceKey: '1',
							processDefinitionKey: '10',
							state: 'COMPLETED',
							operationType: 'CANCEL_PROCESS_INSTANCE',
							errorMessage: null,
						},
					],
					page: {totalItems: 1},
				}),
			}),
		);

		const screen = await renderInstancesTable({...BASE_SEARCH, batchOperationKey: 'batch-1'});

		await expect.element(screen.getByRole('cell', {name: 'Operation State'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'COMPLETED'})).toBeVisible();
	});

	it('should show every row with an active operation, even when one row has several items', async ({worker}) => {
		const activeItem = (itemKey: string, processInstanceKey: string, operationType: string) => ({
			batchOperationKey: 'batch-1',
			itemKey,
			processInstanceKey,
			processDefinitionKey: '10',
			state: 'ACTIVE',
			operationType,
			errorMessage: null,
		});
		worker.use(
			mockInstances([
				createProcessInstance({processInstanceKey: '1', processDefinitionName: 'Order process'}),
				createProcessInstance({processInstanceKey: '2', processDefinitionName: 'Shipping process'}),
			]),
			http.post(endpoints.queryBatchOperationItems.getUrl(), async ({request}) => {
				const {page} = (await request.json()) as {page?: {after?: string}};
				return HttpResponse.json(
					page?.after === undefined
						? {
								items: [
									activeItem('a', '1', 'CANCEL_PROCESS_INSTANCE'),
									activeItem('b', '1', 'SUSPEND_PROCESS_INSTANCE'),
								],
								page: {totalItems: 3, hasMoreTotalItems: true, endCursor: 'cursor-1'},
							}
						: {
								items: [activeItem('c', '2', 'CANCEL_PROCESS_INSTANCE')],
								page: {totalItems: 3, hasMoreTotalItems: false, endCursor: null},
							},
				);
			}),
		);

		const screen = await renderInstancesTable();

		await expect
			.element(screen.getByRole('row', {name: /Shipping process/}).getByTestId('operation-spinner'))
			.toBeVisible();
	});

	it('should announce the operation state loading once', async ({worker}) => {
		worker.use(
			mockInstances([
				createProcessInstance({processInstanceKey: '1'}),
				createProcessInstance({processInstanceKey: '2'}),
			]),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json({items: [], page: {totalItems: 0}}),
				delay: 'infinite',
			}),
		);

		const screen = await renderInstancesTable({...BASE_SEARCH, batchOperationKey: 'batch-1'});

		await expect.element(screen.getByRole('status').first()).toHaveTextContent('Loading...');
	});

	it('should show the empty message with a hint when no state filter is selected', async ({worker}) => {
		worker.use(mockInstances([]));

		const screen = await renderInstancesTable({...BASE_SEARCH, active: false, incidents: false, suspended: false});

		await expect.element(screen.getByText('There are no Instances matching this filter set')).toBeVisible();
		await expect.element(screen.getByText('To see some results, select at least one Instance state')).toBeVisible();
	});

	it('should not show the empty message while the first page is loading', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryProcessInstancesResponse({items: []})),
				delay: 'infinite',
			}),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByRole('region', {name: 'Process Instances Panel'})).toBeVisible();
		await expect.element(screen.getByText('There are no Instances matching this filter set')).not.toBeInTheDocument();
	});

	it('should show the error message when the search fails', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: new HttpResponse(null, {status: 500}),
			}),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
	});

	it('should select a row and reflect it in the selection count', async ({worker}) => {
		worker.use(mockInstances([createProcessInstance({processInstanceKey: '1'})]));

		const screen = await renderInstancesTable();

		await userEvent.click(screen.getByRole('checkbox', {name: 'Select instance 1'}));

		await expect.element(screen.getByRole('checkbox', {name: 'Select instance 1'})).toBeChecked();
	});
});
