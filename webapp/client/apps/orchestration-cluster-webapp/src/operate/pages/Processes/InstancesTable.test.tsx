/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockCancelProcessInstanceEndpoint,
	mockGetBatchOperationEndpoint,
	mockGetProcessInstanceCallHierarchyEndpoint,
	mockGetProcessInstanceEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockQueryProcessInstancesEndpoint,
	mockResolveProcessInstanceIncidentsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createBatchOperation,
	createBatchOperationItem,
	createQueryBatchOperationItemsResponse,
} from '#/shared-test-modules/api-mocks/batch-operations';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {useState} from 'react';
import {userEvent} from 'vitest/browser';
import {Notifications} from '#/shared/notifications/components/Notifications';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {InstancesTable} from './InstancesTable';
import type {ProcessesSearch} from './processesFilter';

const BASE_SEARCH: ProcessesSearch = {active: true, incidents: true, completed: false, canceled: false};

function renderInstancesTable(search: ProcessesSearch = BASE_SEARCH) {
	return renderWithRouter(
		() => (
			// The table's scroll container is `height: 100%` and needs a sized ancestor, which the
			// full page's Frame/ResizablePanel normally provides — give it one here in isolation.
			<div style={{height: '100vh'}}>
				<InstancesTable search={search} />
				<Notifications />
			</div>
		),
		{path: '/operate/processes'},
	);
}

// Renders the table with a button that swaps in a second search, so a filter or sort change can be
// driven the way the page drives it — by handing the component a new search — rather than remounting.
function renderSearchHarness(initial: ProcessesSearch, next: ProcessesSearch) {
	function Harness() {
		const [search, setSearch] = useState(initial);
		return (
			<div style={{height: '100vh'}}>
				<button type="button" onClick={() => setSearch(next)}>
					change search
				</button>
				<InstancesTable search={search} />
			</div>
		);
	}

	return renderWithRouter(Harness, {path: '/operate/processes'});
}

function mockNoBatchOperationItems() {
	return mockQueryBatchOperationItemsEndpoint({
		successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse({items: []})),
	});
}

describe('<InstancesTable />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		vi.useRealTimers();
		sessionStorage.clear();
		notificationsStore.reset();
	});

	it('should render process instance rows', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [
							createProcessInstance({processInstanceKey: '1', processDefinitionName: 'Order Process'}),
							createProcessInstance({processInstanceKey: '2', processDefinitionName: 'Payment Process'}),
						],
					}),
				),
			}),
			mockNoBatchOperationItems(),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByText('Order Process')).toBeVisible();
		await expect.element(screen.getByText('Payment Process')).toBeVisible();
	});

	it('should show a loading table instead of the empty state while initially fetching', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryProcessInstancesResponse()),
				delay: 'infinite',
			}),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByRole('table')).toBeVisible();
		await expect.element(screen.getByText('There are no Instances matching this filter set')).not.toBeInTheDocument();
	});

	it('should link each instance key to its process instance page', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({items: [createProcessInstance({processInstanceKey: '42'})]}),
				),
			}),
			mockNoBatchOperationItems(),
		);

		const screen = await renderInstancesTable();

		await expect
			.element(screen.getByRole('link', {name: 'View instance 42'}))
			.toHaveAttribute('href', '/operate/processes/42');
	});

	it('should show the version tag and business ID columns only when a row has one', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({items: [createProcessInstance({processInstanceKey: '1'})]}),
				),
			}),
			mockNoBatchOperationItems(),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByRole('columnheader', {name: 'Name'})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: 'Version Tag'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('columnheader', {name: 'Business ID'})).not.toBeInTheDocument();
	});

	it('should show the version tag and business ID columns when a row has them', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [
							createProcessInstance({
								processInstanceKey: '1',
								processDefinitionVersionTag: 'v1.2',
								businessId: 'order-1',
							}),
						],
					}),
				),
			}),
			mockNoBatchOperationItems(),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByText('v1.2')).toBeVisible();
		await expect.element(screen.getByText('order-1')).toBeVisible();
	});

	it('should render an incident icon for an instance with an incident', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '1', state: 'ACTIVE', hasIncident: true})],
					}),
				),
			}),
			mockNoBatchOperationItems(),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByTestId('INCIDENT-icon')).toBeVisible();
	});

	it('should show the empty message with a hint when no instance state is selected', async () => {
		const screen = await renderInstancesTable({
			active: false,
			incidents: false,
			completed: false,
			canceled: false,
		});

		await expect.element(screen.getByText('There are no Instances matching this filter set')).toBeVisible();
		await expect.element(screen.getByText('To see some results, select at least one Instance state')).toBeVisible();
	});

	it('should still render when a hand-edited URL carries an unparseable date', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '1', processDefinitionName: 'Order Process'})],
					}),
				),
			}),
			mockNoBatchOperationItems(),
		);

		const screen = await renderInstancesTable({...BASE_SEARCH, startDateFrom: 'not-a-date'});

		await expect.element(screen.getByText('Order Process')).toBeVisible();
	});

	it('should clear stale rows when the last instance state filter is removed', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '1', processDefinitionName: 'Order Process'})],
					}),
				),
			}),
			mockNoBatchOperationItems(),
		);

		const screen = await renderSearchHarness(BASE_SEARCH, {
			active: false,
			incidents: false,
			completed: false,
			canceled: false,
		});
		await expect.element(screen.getByText('Order Process')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'change search'}));

		await expect.element(screen.getByText('Order Process')).not.toBeInTheDocument();
		await expect.element(screen.getByText('There are no Instances matching this filter set')).toBeVisible();
	});

	it('should not show the operation state column outside a batch operation filter', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({items: [createProcessInstance({processInstanceKey: '1'})]}),
				),
			}),
			mockNoBatchOperationItems(),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByRole('columnheader', {name: 'Operation State'})).not.toBeInTheDocument();
	});

	it('should report each instance operation state when filtering by a batch operation', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '1'}), createProcessInstance({processInstanceKey: '2'})],
					}),
				),
			}),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [
							createBatchOperationItem({processInstanceKey: '1', state: 'COMPLETED'}),
							createBatchOperationItem({processInstanceKey: '2', state: 'FAILED', errorMessage: 'boom'}),
						],
					}),
				),
			}),
			mockNoBatchOperationItems(),
		);

		const screen = await renderInstancesTable({...BASE_SEARCH, batchOperationKey: 'batch-op-1'});

		await expect.element(screen.getByRole('columnheader', {name: 'Operation State'})).toBeVisible();
		await expect.element(screen.getByText('COMPLETED')).toBeVisible();
		await expect.element(screen.getByText('FAILED')).toBeVisible();
	});

	it('should report all operation item states for the same process instance', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '1'})],
					}),
				),
			}),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [
							createBatchOperationItem({itemKey: 'item-1', processInstanceKey: '1', state: 'COMPLETED'}),
							createBatchOperationItem({itemKey: 'item-2', processInstanceKey: '1', state: 'FAILED'}),
						],
					}),
				),
			}),
		);

		const screen = await renderInstancesTable({...BASE_SEARCH, batchOperationKey: 'batch-op-1'});

		await expect.element(screen.getByText('FAILED, COMPLETED')).toBeVisible();
	});

	it('should refresh active operation item states until they finish', async ({worker}) => {
		vi.useFakeTimers({shouldAdvanceTime: true});
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '1'})],
					}),
				),
			}),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [createBatchOperationItem({processInstanceKey: '1', state: 'ACTIVE'})],
					}),
				),
			}),
		);

		const screen = await renderInstancesTable({...BASE_SEARCH, batchOperationKey: 'batch-op-1'});
		await expect.element(screen.getByText('ACTIVE')).toBeVisible();

		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [createBatchOperationItem({processInstanceKey: '1', state: 'COMPLETED'})],
					}),
				),
			}),
		);
		await vi.advanceTimersByTimeAsync(5000);

		await expect.element(screen.getByText('COMPLETED')).toBeVisible();
	});

	it('should report an operation item request failure instead of an empty state', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '1'})],
					}),
				),
			}),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json({}, {status: 403}),
			}),
		);

		const screen = await renderInstancesTable({...BASE_SEARCH, batchOperationKey: 'batch-op-1'});

		await expect.element(screen.getByText('Data could not be fetched')).toBeVisible();
	});

	it('should announce operation state loading once without assertive cell announcements', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '1'}), createProcessInstance({processInstanceKey: '2'})],
					}),
				),
			}),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
				delay: 'infinite',
			}),
		);

		const screen = await renderInstancesTable({...BASE_SEARCH, batchOperationKey: 'batch-op-1'});
		await expect.element(screen.getByRole('columnheader', {name: 'Operation State'})).toBeVisible();

		expect(document.querySelectorAll('[aria-live="assertive"]')).toHaveLength(0);
		await expect.element(screen.getByText('Loading...')).toHaveAttribute('aria-live', 'polite');
	});

	it('should fall back to a placeholder for an instance with no matching operation item', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						// An end date keeps `--` unique to the operation-state cell.
						items: [
							createProcessInstance({
								processInstanceKey: '1',
								state: 'COMPLETED',
								endDate: '2026-01-15T11:00:00.000Z',
							}),
						],
					}),
				),
			}),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse({items: []})),
			}),
		);

		const screen = await renderInstancesTable({...BASE_SEARCH, batchOperationKey: 'batch-op-1'});

		await expect.element(screen.getByRole('columnheader', {name: 'Operation State'})).toBeVisible();
		await expect.element(screen.getByText('--')).toBeVisible();
	});

	it('should show the parent instance link only when the instance has a parent', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [
							createProcessInstance({processInstanceKey: '1', parentProcessInstanceKey: '99'}),
							createProcessInstance({processInstanceKey: '2', parentProcessInstanceKey: null}),
						],
					}),
				),
			}),
			mockNoBatchOperationItems(),
		);

		const screen = await renderInstancesTable();

		await expect
			.element(screen.getByRole('link', {name: 'View parent instance 99'}))
			.toHaveAttribute('href', '/operate/processes/99');
		await expect.element(screen.getByText('None')).toBeVisible();
	});

	it('should offer retry and cancel for an active instance with an incident', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '1', state: 'ACTIVE', hasIncident: true})],
					}),
				),
			}),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse({items: []})),
			}),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByRole('button', {name: /retry/i})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: /cancel/i})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: /delete/i})).not.toBeInTheDocument();
	});

	it('should offer cancel for a suspended instance', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '1', state: 'SUSPENDED'})],
					}),
				),
			}),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse({items: []})),
			}),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByRole('button', {name: /cancel/i})).toBeVisible();
	});

	it('should wait for a suspended instance cancellation to finish', async ({worker}) => {
		let stateRequests = 0;
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '1', state: 'SUSPENDED'})],
					}),
				),
			}),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse({items: []})),
			}),
			mockGetProcessInstanceCallHierarchyEndpoint({
				successResponse: HttpResponse.json([]),
			}),
			mockCancelProcessInstanceEndpoint({
				successResponse: new HttpResponse(null, {status: 204}),
			}),
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(createProcessInstance({processInstanceKey: '1', state: 'SUSPENDED'})),
			}),
		);
		worker.events.on('request:start', ({request}) => {
			if (request.method === 'GET' && request.url.endsWith('/v2/process-instances/1')) {
				stateRequests += 1;
			}
		});

		const screen = await renderInstancesTable();
		await userEvent.click(screen.getByRole('button', {name: /cancel/i}));
		await userEvent.click(screen.getByRole('button', {name: 'Apply', exact: true}));

		await expect.poll(() => stateRequests).toBe(1);
		await expect.element(screen.getByTestId('operation-spinner')).toBeVisible();

		worker.use(
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(createProcessInstance({processInstanceKey: '1', state: 'TERMINATED'})),
			}),
		);

		await expect.poll(() => stateRequests, {timeout: 3000}).toBe(2);
		await expect.element(screen.getByTestId('operation-spinner')).not.toBeInTheDocument();
	});

	it('should offer only delete for a finished instance', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [
							createProcessInstance({
								processInstanceKey: '1',
								state: 'COMPLETED',
								endDate: '2026-01-15T11:00:00.000Z',
							}),
						],
					}),
				),
			}),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse({items: []})),
			}),
		);

		const screen = await renderInstancesTable({...BASE_SEARCH, completed: true});

		await expect.element(screen.getByRole('button', {name: /delete/i})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: /cancel/i})).not.toBeInTheDocument();
	});

	it('should send the incident retry command for the clicked row', async ({worker}) => {
		let resolveRequests = 0;
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '1', state: 'ACTIVE', hasIncident: true})],
					}),
				),
			}),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse({items: []})),
			}),
			mockResolveProcessInstanceIncidentsEndpoint({
				successResponse: HttpResponse.json({batchOperationKey: 'batch-op-1'}),
			}),
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createBatchOperation({batchOperationKey: 'batch-op-1', state: 'COMPLETED'})),
			}),
		);
		worker.events.on('request:start', ({request}) => {
			if (request.url.includes('/incident-resolution')) {
				resolveRequests += 1;
			}
		});

		const screen = await renderInstancesTable();

		await userEvent.click(screen.getByRole('button', {name: /retry/i}));

		await expect.poll(() => resolveRequests).toBe(1);
	});

	it('should notify the user when an operation fails', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '1', state: 'ACTIVE', hasIncident: true})],
					}),
				),
			}),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse({items: []})),
			}),
			mockResolveProcessInstanceIncidentsEndpoint({
				successResponse: new HttpResponse(null, {status: 500, statusText: 'Internal Server Error'}),
			}),
		);

		const screen = await renderInstancesTable();

		await userEvent.click(screen.getByRole('button', {name: /retry/i}));

		await expect.element(screen.getByText('Failed to retry incidents')).toBeVisible();
		await expect.element(screen.getByText('Internal Server Error')).toBeVisible();
	});

	it('should warn the user when incident retry is forbidden', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '1', state: 'ACTIVE', hasIncident: true})],
					}),
				),
			}),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse({items: []})),
			}),
			mockResolveProcessInstanceIncidentsEndpoint({
				successResponse: new HttpResponse(null, {status: 403}),
			}),
		);

		const screen = await renderInstancesTable();

		await userEvent.click(screen.getByRole('button', {name: /retry/i}));

		await expect.element(screen.getByText("You don't have permission to perform this operation")).toBeVisible();
		await expect.element(screen.getByText('Please contact the administrator if you need access.')).toBeVisible();
		expect(document.querySelector('.cds--toast-notification--warning')).not.toBeNull();
	});
});
