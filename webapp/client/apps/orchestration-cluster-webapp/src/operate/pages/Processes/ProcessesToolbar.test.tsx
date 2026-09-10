/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Button} from '@carbon/react';
import type {BatchOperation} from '@camunda/camunda-api-zod-schemas/8.10';
import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {HttpResponse} from 'msw';
import {createInstance} from 'i18next';
import {I18nextProvider} from 'react-i18next';
import {cleanup, render} from 'vitest-browser-react';
import {z} from 'zod';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockQueryProcessInstancesEndpoint,
	mockCreateCancellationBatchOperationEndpoint,
	mockCreateIncidentResolutionBatchOperationEndpoint,
	mockCreateDeletionBatchOperationEndpoint,
	mockGetBatchOperationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {createBatchOperation} from '#/shared-test-modules/api-mocks/batch-operations';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {Notifications} from '#/shared/notifications/components/Notifications';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {translationResources} from '#/shared/i18n';
import {InstancesTable} from './InstancesTable';
import {ProcessesToolbar} from './ProcessesToolbar';
import {mapProcessInstancesFilter, type ProcessesSearch} from './processesFilter';
import {useProcessInstancesSelection} from './useProcessInstancesSelection';

const SEARCH: ProcessesSearch = {
	active: true,
	incidents: true,
	completed: true,
	canceled: true,
	tenantId: 'tenant-a',
	process: 'orders',
	version: 2,
};
const ITEMS = [
	createProcessInstance({processInstanceKey: '1', state: 'ACTIVE', hasIncident: false}),
	createProcessInstance({processInstanceKey: '2', state: 'ACTIVE', hasIncident: true}),
	createProcessInstance({processInstanceKey: '3', state: 'COMPLETED', hasIncident: false}),
	createProcessInstance({processInstanceKey: '4', state: 'TERMINATED', hasIncident: false}),
	createProcessInstance({processInstanceKey: '5', state: 'SUSPENDED', hasIncident: true}),
];
const list = (totalItems = 10, hasMoreTotalItems = false) =>
	mockQueryProcessInstancesEndpoint({
		successResponse: HttpResponse.json(
			createQueryProcessInstancesResponse({items: ITEMS, page: {totalItems, hasMoreTotalItems}}),
		),
	});
const accepted = (batchOperationType: BatchOperation['batchOperationType'] = 'CANCEL_PROCESS_INSTANCE') =>
	HttpResponse.json({batchOperationKey: 'batch-op-1', batchOperationType}, {status: 202});
const completed = (batchOperationType: BatchOperation['batchOperationType'] = 'CANCEL_PROCESS_INSTANCE') =>
	mockGetBatchOperationEndpoint({successResponse: HttpResponse.json(createBatchOperation({batchOperationType}))});

function renderTable(initial = SEARCH, isActionMode = false) {
	function Harness() {
		const [search, setSearch] = useState(initial);
		return (
			<div style={{height: '100vh'}}>
				<Button onClick={() => setSearch({...search, tenantId: 'tenant-b'})}>Change tenant</Button>
				<Button onClick={() => setSearch({...search, sort: 'startDate+asc'})}>Change sort</Button>
				<InstancesTable search={search} isActionMode={isActionMode} />
				<Notifications />
			</div>
		);
	}
	return renderWithRouter(Harness, {path: '/operate/processes'});
}

async function select(screen: Awaited<ReturnType<typeof renderTable>>, key?: string) {
	await userEvent.click(
		screen.getByRole('checkbox', {name: key ? `Select instance ${key}` : 'Select all items', exact: true}),
		{force: true},
	);
}

describe('Processes bulk toolbar', () => {
	beforeEach(() => sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration())));
	afterEach(async () => {
		await cleanup();
		sessionStorage.clear();
		notificationsStore.reset();
		vi.useRealTimers();
	});

	it('should derive checked-row eligibility from running, finished and incident states', async ({worker}) => {
		worker.use(list());
		const screen = await renderTable();
		await expect.element(screen.getByRole('button', {name: 'Delete', exact: true})).not.toBeInTheDocument();
		await select(screen, '1');
		await expect.element(screen.getByRole('button', {name: 'Cancel', exact: true})).toBeEnabled();
		await expect.element(screen.getByRole('button', {name: 'Delete', exact: true})).toBeDisabled();
		await expect.element(screen.getByRole('button', {name: 'Retry', exact: true})).toBeDisabled();
		await select(screen, '2');
		await select(screen, '3');
		await expect.element(screen.getByRole('button', {name: 'Retry', exact: true})).toBeEnabled();
		await expect.element(screen.getByRole('button', {name: 'Delete', exact: true})).toBeEnabled();
		await expect.element(screen.getByRole('checkbox', {name: 'Select all items'})).toBePartiallyChecked();
		await userEvent.click(screen.getByRole('button', {name: 'Discard'}));
		await select(screen, '5');
		await expect.element(screen.getByRole('button', {name: 'Retry', exact: true})).toBeDisabled();
	});

	for (const [action, mock, keys, message, batchOperationType, operationLabel] of [
		[
			'Delete',
			mockCreateDeletionBatchOperationEndpoint,
			['3'],
			'This permanently deletes',
			'DELETE_PROCESS_INSTANCE',
			'Delete Process Instance',
		],
		[
			'Cancel',
			mockCreateCancellationBatchOperationEndpoint,
			['1', '2'],
			'Finished instances in your selection will be ignored.',
			'CANCEL_PROCESS_INSTANCE',
			'Cancel Process Instance',
		],
		[
			'Retry',
			mockCreateIncidentResolutionBatchOperationEndpoint,
			['1', '2'],
			'Instances without an incident in your selection will be ignored.',
			'RESOLVE_INCIDENT',
			'Resolve Incident',
		],
	] as const) {
		it(`should confirm and submit ${action} with eligible keys and tenant/definition filters`, async ({worker}) => {
			const received = vi.fn(() => true);
			worker.use(
				list(),
				completed(batchOperationType),
				mock({
					schema: z.custom(received),
					successResponse: accepted(batchOperationType),
					failureResponse: new HttpResponse(null, {status: 400}),
				}),
			);
			const screen = await renderTable();
			for (const key of ['1', '2', '3']) {
				await select(screen, key);
			}
			await userEvent.click(screen.getByRole('button', {name: action, exact: true}));
			const modal = screen.getByRole('dialog');
			await expect.element(modal.getByText(message, {exact: false})).toBeVisible();
			if (action === 'Cancel') {
				await expect
					.element(modal.getByText('In case there are called instances, these will be canceled too.', {exact: false}))
					.toBeVisible();
			}
			await userEvent.click(modal.getByRole('button', {name: action === 'Delete' ? 'Delete' : 'Apply', exact: true}));
			await expect.element(screen.getByText(`The batch operation "${operationLabel}" has been started`)).toBeVisible();
			expect(received).toHaveBeenCalledWith({
				filter: {...mapProcessInstancesFilter(SEARCH), processInstanceKey: {$in: keys}},
			});
			await expect.element(screen.getByRole('button', {name: 'Go to operation details'})).toBeVisible();
			await expect.element(screen.getByRole('checkbox', {name: 'Select instance 1', exact: true})).not.toBeChecked();
		});
	}

	it('should apply filter-wide selection and exclusions with a truncated count', async ({worker}) => {
		const received = vi.fn(() => true);
		worker.use(
			list(100, true),
			completed(),
			mockCreateCancellationBatchOperationEndpoint({
				schema: z.custom(received),
				successResponse: accepted(),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);
		const screen = await renderTable();
		await select(screen);
		await expect.element(screen.getByText('100+ items selected')).toBeVisible();
		await select(screen, '1');
		await expect.element(screen.getByText('99+ items selected')).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Cancel', exact: true}));
		await expect
			.element(screen.getByRole('dialog').getByText('99+ instances selected for "Cancel" operation.', {exact: false}))
			.toBeVisible();
		await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Apply'}));
		await expect.element(screen.getByRole('button', {name: 'Go to operation details'})).toBeVisible();
		expect(received).toHaveBeenCalledWith({
			filter: {...mapProcessInstancesFilter(SEARCH), processInstanceKey: {$notIn: ['1']}},
		});
	});

	it('should use the selected state filter rather than the visible rows for all-result eligibility', async ({
		worker,
	}) => {
		worker.use(list(100));
		const screen = await renderTable({...SEARCH, active: false, incidents: false, canceled: false, completed: true});
		await select(screen);
		await expect.element(screen.getByRole('button', {name: 'Delete', exact: true})).toBeEnabled();
		await expect.element(screen.getByRole('button', {name: 'Cancel', exact: true})).toBeDisabled();
		await expect.element(screen.getByRole('button', {name: 'Retry', exact: true})).toBeDisabled();
		await select(screen);
		await expect.element(screen.getByRole('button', {name: 'Delete', exact: true})).not.toBeInTheDocument();
	});

	for (const [action, mock, batchOperationType] of [
		['Delete', mockCreateDeletionBatchOperationEndpoint, 'DELETE_PROCESS_INSTANCE'],
		['Cancel', mockCreateCancellationBatchOperationEndpoint, 'CANCEL_PROCESS_INSTANCE'],
		['Retry', mockCreateIncidentResolutionBatchOperationEndpoint, 'RESOLVE_INCIDENT'],
	] as const) {
		it(`should never widen an instance-key filter when excluding rows for ${action}`, async ({worker}) => {
			const received = vi.fn(() => true);
			const search = {...SEARCH, processInstanceKey: '1,2,3,4,5'};
			worker.use(
				list(5),
				completed(batchOperationType),
				mock({
					schema: z.custom(received),
					successResponse: accepted(batchOperationType),
					failureResponse: new HttpResponse(null, {status: 400}),
				}),
			);
			const screen = await renderTable(search);
			await select(screen);
			await select(screen, '1');
			await userEvent.click(screen.getByRole('button', {name: action, exact: true}));
			await userEvent.click(
				screen.getByRole('dialog').getByRole('button', {name: action === 'Delete' ? 'Delete' : 'Apply', exact: true}),
			);
			await expect.element(screen.getByRole('button', {name: 'Go to operation details'})).toBeVisible();
			expect(received).toHaveBeenCalledWith({
				filter: {
					...mapProcessInstancesFilter(search),
					processInstanceKey: {$in: ['1', '2', '3', '4', '5'], $notIn: ['1']},
				},
			});
		});
	}

	it('should retain eligibility and included keys when selected rows leave the loaded page', async ({worker}) => {
		const received = vi.fn(() => true);
		worker.use(
			list(),
			completed('DELETE_PROCESS_INSTANCE'),
			mockCreateDeletionBatchOperationEndpoint({
				schema: z.custom(received),
				successResponse: accepted('DELETE_PROCESS_INSTANCE'),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);
		const screen = await renderTable();
		await select(screen, '1');
		await select(screen, '3');
		await select(screen, '4');
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({items: [ITEMS[2]!], page: {totalItems: 10}}),
				),
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Change sort'}));
		await expect
			.element(screen.getByRole('checkbox', {name: 'Select instance 4', exact: true}))
			.not.toBeInTheDocument();
		await expect.element(screen.getByText('3 items selected')).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Delete', exact: true}));
		await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Delete', exact: true}));
		await expect.element(screen.getByRole('button', {name: 'Go to operation details'})).toBeVisible();
		expect(received).toHaveBeenCalledWith({
			filter: {...mapProcessInstancesFilter(SEARCH), processInstanceKey: {$in: ['3', '4']}},
		});
	});

	it('should preserve selection on dialog close but discard on secondary cancel', async ({worker}) => {
		worker.use(list());
		const screen = await renderTable();
		await select(screen, '3');
		await userEvent.click(screen.getByRole('button', {name: 'Delete', exact: true}));
		await userEvent.keyboard('{Escape}');
		await expect.element(screen.getByRole('checkbox', {name: 'Select instance 3', exact: true})).toBeChecked();
		await userEvent.click(screen.getByRole('button', {name: 'Delete', exact: true}));
		await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Cancel', exact: true}));
		await expect.element(screen.getByRole('checkbox', {name: 'Select instance 3', exact: true})).not.toBeChecked();
	});

	it('should preserve selection on sort but reset it when tenant filters change', async ({worker}) => {
		worker.use(list());
		const screen = await renderTable();
		await select(screen, '1');
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryProcessInstancesResponse()),
				delay: 'infinite',
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Change sort'}));
		await expect.element(screen.getByRole('checkbox', {name: 'Select instance 1', exact: true})).toBeChecked();
		await expect.element(screen.getByText('1 item selected')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Cancel', exact: true})).toBeEnabled();
		worker.use(list());
		await userEvent.click(screen.getByRole('button', {name: 'Change tenant'}));
		await expect.element(screen.getByRole('checkbox', {name: 'Select instance 1', exact: true})).not.toBeChecked();
		await expect.element(screen.getByRole('button', {name: 'Discard'})).not.toBeInTheDocument();
	});

	for (const [response, title] of [
		[new HttpResponse(null, {status: 403}), "You don't have permission to perform this operation"],
		[new HttpResponse(null, {status: 500}), 'Operation could not be created'],
		[HttpResponse.error(), 'Operation could not be created'],
	] as const) {
		it(`should recover from ${response.status} without dropping selection`, async ({worker}) => {
			worker.use(list(), mockCreateCancellationBatchOperationEndpoint({successResponse: response}));
			const screen = await renderTable();
			await select(screen, '1');
			await userEvent.click(screen.getByRole('button', {name: 'Cancel', exact: true}));
			await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Apply'}));
			await expect.element(screen.getByText(title)).toBeVisible();
			await expect.element(screen.getByRole('checkbox', {name: 'Select instance 1', exact: true})).toBeChecked();
			worker.use(completed(), mockCreateCancellationBatchOperationEndpoint({successResponse: accepted()}));
			await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
			await userEvent.click(screen.getByRole('button', {name: 'Cancel', exact: true}));
			await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Apply'}));
			await expect.element(screen.getByRole('button', {name: 'Go to operation details'})).toBeVisible();
		});
	}

	it('should disable submission while a request is pending and during downstream action mode', async ({worker}) => {
		worker.use(list(), mockCreateCancellationBatchOperationEndpoint({successResponse: accepted(), delay: 'infinite'}));
		const screen = await renderTable();
		await select(screen, '1');
		await userEvent.click(screen.getByRole('button', {name: 'Cancel', exact: true}));
		await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Apply'}));
		await expect.element(screen.getByRole('button', {name: 'Cancel', exact: true})).toBeDisabled();
		await screen.unmount();
		const modeScreen = await renderTable(SEARCH, true);
		await select(modeScreen);
		for (const name of ['Delete', 'Cancel', 'Retry']) {
			await expect.element(modeScreen.getByRole('button', {name, exact: true})).toBeDisabled();
		}
	});

	for (const mode of ['include', 'exclude'] as const) {
		it(`should expose a ${mode} request without an undefined equality criterion`, async () => {
			const received = vi.fn();
			function Harness() {
				const selection = useProcessInstancesSelection(SEARCH, ITEMS, 10, false);
				return (
					<>
						<Button onClick={selection.selectAll}>Select all</Button>
						<Button onClick={() => selection.toggle('1')}>Toggle instance</Button>
						<Button onClick={() => received(selection.getRequest('cancel'))}>Read request</Button>
					</>
				);
			}
			const screen = await render(<Harness />);
			if (mode === 'exclude') {
				await userEvent.click(screen.getByRole('button', {name: 'Select all', exact: true}));
			}
			await userEvent.click(screen.getByRole('button', {name: 'Toggle instance'}));
			await userEvent.click(screen.getByRole('button', {name: 'Read request'}));
			expect(received.mock.calls[0]?.[0].filter.processInstanceKey).toStrictEqual(
				mode === 'include' ? {$in: ['1']} : {$notIn: ['1']},
			);
		});
	}

	it('should keep an absent filter identity stable and clear selection across filter transitions', async () => {
		const emptySearch = {...SEARCH, active: false, incidents: false, completed: false, canceled: false};
		const received = vi.fn();
		function Harness() {
			const [search, setSearch] = useState(emptySearch);
			const selection = useProcessInstancesSelection(search, ITEMS, 10, false);
			return (
				<>
					<Button onClick={() => setSearch(SEARCH)}>Apply filters</Button>
					<Button onClick={() => setSearch(emptySearch)}>Clear filters</Button>
					<Button onClick={() => selection.toggle('1')}>Select instance</Button>
					<Button
						onClick={() =>
							received({
								identity: selection.filterIdentity,
								count: selection.selectedCount,
								request: selection.getRequest('cancel'),
							})
						}
					>
						Read selection
					</Button>
				</>
			);
		}
		const screen = await render(<Harness />);
		await userEvent.click(screen.getByRole('button', {name: 'Read selection'}));
		expect(received).toHaveBeenLastCalledWith({identity: 'null', count: 0, request: {filter: {}}});

		await userEvent.click(screen.getByRole('button', {name: 'Apply filters'}));
		await userEvent.click(screen.getByRole('button', {name: 'Select instance', exact: true}));
		await userEvent.click(screen.getByRole('button', {name: 'Read selection'}));
		expect(received).toHaveBeenLastCalledWith({
			identity: JSON.stringify(mapProcessInstancesFilter(SEARCH)),
			count: 1,
			request: {filter: {...mapProcessInstancesFilter(SEARCH), processInstanceKey: {$in: ['1']}}},
		});

		await userEvent.click(screen.getByRole('button', {name: 'Clear filters'}));
		await userEvent.click(screen.getByRole('button', {name: 'Read selection'}));
		expect(received).toHaveBeenLastCalledWith({identity: 'null', count: 0, request: {filter: {}}});

		await userEvent.click(screen.getByRole('button', {name: 'Apply filters'}));
		await userEvent.click(screen.getByRole('button', {name: 'Read selection'}));
		expect(received).toHaveBeenLastCalledWith({
			identity: JSON.stringify(mapProcessInstancesFilter(SEARCH)),
			count: 0,
			request: {filter: mapProcessInstancesFilter(SEARCH)},
		});
	});

	for (const language of ['en', 'de', 'fr', 'es']) {
		for (const action of ['delete', 'cancel', 'retry'] as const) {
			for (const count of [1, 3]) {
				it(`should preserve the translated ${language} ${action} label in a ${count}-instance confirmation`, async () => {
					const translations = createInstance();
					await translations.init({
						lng: language,
						resources: translationResources,
						interpolation: {escapeValue: false},
					});
					function Harness() {
						const selection = useProcessInstancesSelection(SEARCH, ITEMS, 10, false);
						const keys = count === 3 ? ['1', '2', '3'] : [action === 'delete' ? '3' : '2'];
						return (
							<>
								<Button onClick={() => keys.forEach(selection.toggle)}>Select instances</Button>
								<ProcessesToolbar selection={selection} isSubmitting={false} onSubmit={vi.fn()} />
							</>
						);
					}
					const screen = await render(
						<I18nextProvider i18n={translations}>
							<Harness />
						</I18nextProvider>,
					);
					const label = translations.t(`operate.processes.toolbar.${action}`);
					await userEvent.click(screen.getByRole('button', {name: 'Select instances'}));
					await userEvent.click(screen.getByRole('button', {name: label, exact: true}));
					await expect
						.element(screen.getByRole('dialog'))
						.toHaveTextContent(
							translations.t('operate.processes.toolbar.confirm', {count, total: `${count}`, action: label}),
						);
				});
			}
		}
	}
});
