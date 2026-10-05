/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect} from 'vitest';
import {page, userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import type {SetupWorker} from 'msw/browser';
import {z} from 'zod';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockCreateModificationBatchOperationEndpoint,
	mockGetBatchOperationEndpoint,
	mockGetProcessDefinitionStatisticsEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {
	createGetProcessDefinitionStatisticsResponse,
	createProcessDefinitionStatistic,
} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createBatchOperation,
	createQueryBatchOperationItemsResponse,
} from '#/shared-test-modules/api-mocks/batch-operations';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {Notifications} from '#/shared/notifications/components/Notifications';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {ProcessesHarness} from './ProcessesHarness';

const HELPER_STORAGE_KEY = 'operate.hideMoveModificationHelperModal';
const DEFAULT_SEARCH = 'process=my_simple_process&version=1&tenantId=tenant-a&elementId=task-1';

const statistics = (active: number, incidents: number) =>
	HttpResponse.json(
		createGetProcessDefinitionStatisticsResponse([
			createProcessDefinitionStatistic({elementId: 'task-1', active, incidents}),
		]),
	);

const accepted = () =>
	HttpResponse.json({batchOperationKey: 'batch-1', batchOperationType: 'MODIFY_PROCESS_INSTANCE'}, {status: 202});

function mockPage(worker: SetupWorker) {
	worker.use(
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [
						createProcessDefinition({
							processDefinitionId: 'my_simple_process',
							processDefinitionKey: '123',
							tenantId: 'tenant-a',
							version: 1,
							name: 'Invoice process',
						}),
					],
				}),
			),
		}),
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [
						createProcessInstance({processInstanceKey: '1', state: 'ACTIVE'}),
						createProcessInstance({processInstanceKey: '2', state: 'ACTIVE'}),
						createProcessInstance({processInstanceKey: '3', state: 'COMPLETED'}),
					],
					page: {totalItems: 3},
				}),
			),
		}),
		mockQueryBatchOperationItemsEndpoint({
			successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
		}),
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
		mockGetProcessDefinitionStatisticsEndpoint({successResponse: statistics(2, 1)}),
	);
}

function renderPage(search = DEFAULT_SEARCH) {
	return renderWithRouter(
		() => (
			<>
				<ProcessesHarness />
				<Notifications />
			</>
		),
		{path: '/operate/processes', initialEntry: `/operate/processes?${search}`},
	);
}

type Screen = Awaited<ReturnType<typeof renderPage>>;

async function selectInstance(screen: Screen, key: string) {
	await userEvent.click(screen.getByRole('checkbox', {name: `Select instance ${key}`}), {force: true});
}

async function enterMode(screen: Screen) {
	await selectInstance(screen, '1');
	await userEvent.click(screen.getByRole('button', {name: 'Move'}));
	await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Continue'}), {force: true});
	await expect.element(screen.getByText('Batch Modification Mode')).toBeVisible();
	await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
}

async function clickDiagramElement(elementId: string) {
	await expect.poll(() => document.querySelector(`[data-element-id="${elementId}"]`)).not.toBeNull();
	await userEvent.click(document.querySelector<SVGElement>(`[data-element-id="${elementId}"]`)!);
}

describe('Processes batch modification', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(async () => {
		await cleanup();
		sessionStorage.clear();
		localStorage.clear();
		notificationsStore.reset();
	});

	it.for([
		{
			condition: 'no running instance is selected',
			search: DEFAULT_SEARCH,
			instanceKey: '3',
			reason: 'You can only move element instances in active or incident state.',
		},
		{
			condition: 'the element type is not supported',
			search: DEFAULT_SEARCH.replace('task-1', 'start_event'),
			instanceKey: '1',
			reason: 'The selected element type is not supported.',
		},
		{
			condition: 'no element is selected',
			search: DEFAULT_SEARCH.replace('&elementId=task-1', ''),
			instanceKey: '1',
			reason: 'Select an element from the diagram first.',
		},
		{
			condition: 'the element is not in the diagram',
			search: DEFAULT_SEARCH.replace('task-1', 'missing_element'),
			instanceKey: '1',
			reason: 'Select an element from the diagram first.',
		},
	])('should disable Move when $condition', async ({search, instanceKey, reason}, {worker}) => {
		mockPage(worker);
		const screen = await renderPage(search);

		await selectInstance(screen, instanceKey);

		await expect.element(screen.getByRole('button', {name: 'Move'})).toBeDisabled();
		await expect.element(screen.getByRole('button', {name: 'Move'})).toHaveAttribute('title', reason);
	});

	it('should ask for a diagram element while the diagram is loading', async ({worker}) => {
		mockPage(worker);
		worker.use(mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML), delay: 'infinite'}));
		const screen = await renderPage();

		await selectInstance(screen, '1');

		await expect.element(screen.getByRole('button', {name: 'Move'})).toBeDisabled();
		await expect
			.element(screen.getByRole('button', {name: 'Move'}))
			.toHaveAttribute('title', 'Select an element from the diagram first.');
	});

	it('should enter the mode through the helper and remember to skip it', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await selectInstance(screen, '1');

		await userEvent.click(screen.getByRole('button', {name: 'Move'}));

		const helper = screen.getByRole('dialog', {name: 'Process instance batch move mode'});
		await expect
			.element(helper)
			.toMatchTextContent('This mode allows you to move multiple instances as a batch in one operation');
		await expect.element(helper).toMatchTextContent('1. Click on the target element.');
		await expect.element(helper.getByRole('img', {name: 'A bpmn diagram with a selected element'})).toBeVisible();
		await expect.element(helper).toMatchTextContent('2. Click “Review Modification”.');

		await userEvent.click(helper.getByRole('button', {name: 'Cancel'}));

		await expect.element(helper).not.toBeInTheDocument();
		await expect.element(screen.getByText('Batch Modification Mode')).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('button', {name: 'Move'}));
		await userEvent.click(helper.getByText("Don't show this message next time"));
		await userEvent.click(helper.getByRole('button', {name: 'Continue'}));

		await expect.element(screen.getByText('Batch Modification Mode')).toBeVisible();
		expect(getStateLocally(HELPER_STORAGE_KEY)).toBe(true);
	});

	it('should enter the mode without the helper once it is hidden', async ({worker}) => {
		mockPage(worker);
		storeStateLocally(HELPER_STORAGE_KEY, true);
		const screen = await renderPage();
		await selectInstance(screen, '1');

		await userEvent.click(screen.getByRole('button', {name: 'Move'}));

		await expect.element(screen.getByText('Batch Modification Mode')).toBeVisible();
		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
	});

	it('should enter the mode without the helper when legacy Operate hid it', async ({worker}) => {
		mockPage(worker);
		localStorage.setItem('sharedState', JSON.stringify({hideMoveModificationHelperModal: true}));
		const screen = await renderPage();
		await selectInstance(screen, '1');

		await userEvent.click(screen.getByRole('button', {name: 'Move'}));

		await expect.element(screen.getByText('Batch Modification Mode')).toBeVisible();
		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
	});

	it.for([
		{condition: 'the legacy Operate preference is malformed', storage: {sharedState: '{'}},
		{
			condition: 'the legacy Operate preference is not a boolean',
			storage: {sharedState: JSON.stringify({hideMoveModificationHelperModal: 'true'})},
		},
		{
			condition: 'the current preference overrides the legacy Operate one',
			storage: {
				sharedState: JSON.stringify({hideMoveModificationHelperModal: true}),
				[HELPER_STORAGE_KEY]: 'false',
			},
		},
	])('should show the helper when $condition', async ({storage}, {worker}) => {
		mockPage(worker);
		Object.entries(storage).forEach(([key, value]) => localStorage.setItem(key, value));
		const screen = await renderPage();
		await selectInstance(screen, '1');

		await userEvent.click(screen.getByRole('button', {name: 'Move'}));

		await expect.element(screen.getByRole('dialog', {name: 'Process instance batch move mode'})).toBeVisible();
		await expect.element(screen.getByText('Batch Modification Mode')).not.toBeInTheDocument();
		expect(localStorage.getItem('sharedState')).toBe(storage.sharedState);
	});

	it('should lock the process filters and the other operations in the mode', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();

		await enterMode(screen);

		await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeDisabled();
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toBeDisabled();
		await expect
			.element(screen.getByRole('combobox', {name: 'Name'}))
			.toHaveAttribute('title', 'Not changeable in batch modification mode');
		await expect.element(screen.getByRole('combobox', {name: 'Version'})).toBeDisabled();
		await expect.element(screen.getByRole('combobox', {name: 'Element'})).toBeDisabled();
		await expect
			.element(screen.getByRole('combobox', {name: 'Element'}))
			.toHaveAttribute('title', 'Not changeable in batch modification mode');
		for (const action of ['Move', 'Cancel']) {
			await expect.element(screen.getByRole('button', {name: action, exact: true})).toBeDisabled();
			await expect
				.element(screen.getByRole('button', {name: action, exact: true}))
				.toHaveAttribute('title', 'Not available in batch modification mode');
		}
	});

	it.for([
		{active: 1, incidents: 0, instances: '1 instance'},
		{active: 2, incidents: 1, instances: '3 instances'},
	])(
		'should schedule a move of $instances to the clicked target and undo it',
		async ({active, incidents, instances}, {worker}) => {
			mockPage(worker);
			worker.use(mockGetProcessDefinitionStatisticsEndpoint({successResponse: statistics(active, incidents)}));
			const screen = await renderPage();
			await enterMode(screen);

			await expect
				.element(screen.getByText('Select where you want to move the selected instances on the diagram.'))
				.toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'Review Modification'})).toBeDisabled();

			await clickDiagramElement('end_event');

			await expect
				.element(
					screen.getByText(
						`Modification scheduled: Move ${instances} from “Review invoice” to “end_event”. Press “Review Modification” button to confirm.`,
					),
				)
				.toBeVisible();
			const badges = screen.getByTestId('modifications-overlay');
			await expect
				.element(badges.filter({has: page.getByTestId('badge-minus-icon')}))
				.toHaveTextContent(String(active + incidents));
			await expect
				.element(badges.filter({has: page.getByTestId('badge-plus-icon')}))
				.toHaveTextContent(String(active + incidents));
			await expect.element(screen.getByRole('button', {name: 'Review Modification'})).toBeEnabled();

			await userEvent.click(screen.getByRole('button', {name: 'Undo'}));

			await expect
				.element(screen.getByText('Select where you want to move the selected instances on the diagram.'))
				.toBeVisible();
			await expect.element(badges).not.toBeInTheDocument();
			await expect.element(screen.getByRole('button', {name: 'Review Modification'})).toBeDisabled();
		},
	);

	it('should count the affected instances with the selection and the Business ID filter', async ({worker}) => {
		mockPage(worker);
		worker.use(
			mockGetProcessDefinitionStatisticsEndpoint({
				schema: z.object({
					filter: z.object({
						processDefinitionId: z.undefined().optional(),
						processDefinitionVersion: z.undefined().optional(),
						businessId: z.strictObject({$eq: z.literal('invoice-123')}),
						processInstanceKey: z.strictObject({$in: z.tuple([z.literal('1')])}),
						state: z.strictObject({$eq: z.literal('ACTIVE')}),
						elementId: z.strictObject({$eq: z.literal('task-1')}),
					}),
				}),
				successResponse: statistics(4, 0),
				failureResponse: statistics(0, 0),
			}),
		);
		const screen = await renderPage(`${DEFAULT_SEARCH}&businessId=eq_invoice-123`);
		await enterMode(screen);

		await clickDiagramElement('end_event');

		await expect
			.element(
				screen.getByText(
					'Modification scheduled: Move 4 instances from “Review invoice” to “end_event”. Press “Review Modification” button to confirm.',
				),
			)
			.toBeVisible();
	});

	it('should keep the target when the selection changes', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterMode(screen);
		await clickDiagramElement('end_event');

		await selectInstance(screen, '2');

		await expect.element(screen.getByRole('button', {name: 'Undo'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Review Modification'})).toBeEnabled();
	});

	it('should confirm exiting the mode and keep the selection', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterMode(screen);

		await userEvent.click(screen.getByRole('button', {name: 'Exit'}));

		const exitDialog = screen.getByRole('dialog', {name: 'Exit batch modification mode'});
		await expect.element(exitDialog).toMatchTextContent('Click “Exit” to proceed.');
		await expect.element(exitDialog).not.toMatchTextContent('About to discard all added modifications');

		await userEvent.click(exitDialog.getByRole('button', {name: 'Cancel'}));
		await clickDiagramElement('end_event');
		await userEvent.click(screen.getByRole('button', {name: 'Exit'}));

		await expect.element(exitDialog).toMatchTextContent('About to discard all added modifications');

		await userEvent.click(exitDialog.getByRole('button', {name: 'Exit'}));

		await expect.element(screen.getByText('Batch Modification Mode')).not.toBeInTheDocument();
		await expect.element(screen.getByRole('checkbox', {name: 'Select instance 1'})).toBeChecked();
		await expect.element(screen.getByRole('button', {name: 'Move'})).toBeEnabled();
		expect(screen.router.state.location.search).toMatchObject({
			process: 'my_simple_process',
			version: 1,
			tenantId: 'tenant-a',
			elementId: 'task-1',
		});
	});

	it('should ask to exit before leaving the page but not when only the search changes, like legacy', async ({
		worker,
	}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterMode(screen);
		const exitDialog = screen.getByRole('dialog', {name: 'Exit batch modification mode'});

		await userEvent.click(screen.getByRole('link', {name: 'View instance 1'}), {force: true});

		await expect.element(exitDialog).toBeVisible();

		await userEvent.click(exitDialog.getByRole('button', {name: 'Cancel'}));

		expect(screen.router.state.location.pathname).toBe('/operate/processes');

		await screen.router.navigate({to: '.', search: (prev) => ({...prev, sort: 'startDate+asc'})});

		expect(screen.router.state.location.search).toMatchObject({sort: 'startDate+asc'});
		await expect.element(exitDialog).not.toBeInTheDocument();
		await expect.element(screen.getByText('Batch Modification Mode')).toBeVisible();
	});

	it('should not count or move active instances outside a selection of only suspended instances', async ({worker}) => {
		mockPage(worker);
		const statisticsFilters: unknown[] = [];
		const onRequest = async ({request}: {request: Request}) => {
			if (request.url.endsWith('/statistics/element-instances')) {
				statisticsFilters.push((await request.clone().json()).filter);
			}
		};
		worker.events.on('request:start', onRequest);

		try {
			const screen = await renderPage();
			await enterMode(screen);
			await screen.router.navigate({to: '.', search: (prev) => ({...prev, active: false, incidents: false})});
			statisticsFilters.length = 0;

			await userEvent.click(screen.getByRole('checkbox', {name: 'Select all items'}), {force: true});
			await clickDiagramElement('end_event');

			await expect.element(screen.getByRole('button', {name: 'Review Modification'})).toBeDisabled();
			expect(statisticsFilters).not.toContainEqual(expect.objectContaining({state: {$eq: 'ACTIVE'}}));
		} finally {
			worker.events.removeListener('request:start', onRequest);
		}
	});

	it('should review the planned move', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterMode(screen);
		await clickDiagramElement('end_event');

		await userEvent.click(screen.getByRole('button', {name: 'Review Modification'}));

		const review = screen.getByRole('dialog', {name: 'Apply Modifications'});
		await expect
			.element(review.getByText('Planned modifications for "Invoice process". Click "Apply" to proceed.'))
			.toBeVisible();
		await expect.element(review.getByRole('heading', {name: 'Element Modifications'})).toBeVisible();
		const row = review.getByRole('row').filter({has: page.getByRole('cell', {name: 'Batch move'})});
		await expect.element(row.getByRole('cell', {name: 'Review invoice --> end_event'})).toBeVisible();
		await expect.element(row.getByRole('cell', {name: '3', exact: true})).toBeVisible();
	});

	it('should not apply the move once a filter change clears the selection', async ({worker}) => {
		mockPage(worker);
		const modificationRequests: unknown[] = [];
		const onRequest = async ({request}: {request: Request}) => {
			if (request.url.endsWith('/process-instances/modification')) {
				modificationRequests.push(await request.clone().json());
			}
		};
		worker.events.on('request:start', onRequest);

		try {
			worker.use(mockCreateModificationBatchOperationEndpoint({successResponse: accepted()}));
			const screen = await renderPage();
			await enterMode(screen);
			await clickDiagramElement('end_event');
			await userEvent.click(screen.getByRole('button', {name: 'Review Modification'}));
			const review = screen.getByRole('dialog', {name: 'Apply Modifications'});
			await expect.element(review.getByRole('button', {name: 'Apply'})).toBeEnabled();

			await screen.router.navigate({to: '.', search: (prev) => ({...prev, businessId: 'eq_invoice-123'})});

			await expect.element(review.getByRole('button', {name: 'Apply'})).toBeDisabled();
			await userEvent.click(review.getByRole('button', {name: 'Apply'}), {force: true});
			await expect.element(screen.getByText('Batch Modification Mode')).toBeVisible();
			expect(modificationRequests).toEqual([]);
		} finally {
			worker.events.removeListener('request:start', onRequest);
		}
	});

	it('should not apply the move once the process exists in more than one tenant', async ({worker}) => {
		mockPage(worker);
		const modificationRequests: unknown[] = [];
		const onRequest = async ({request}: {request: Request}) => {
			if (request.url.endsWith('/process-instances/modification')) {
				modificationRequests.push(await request.clone().json());
			}
		};
		worker.events.on('request:start', onRequest);

		try {
			worker.use(mockCreateModificationBatchOperationEndpoint({successResponse: accepted()}));
			const screen = await renderPage(DEFAULT_SEARCH.replace('&tenantId=tenant-a', ''));
			await enterMode(screen);
			await clickDiagramElement('end_event');
			await userEvent.click(screen.getByRole('button', {name: 'Review Modification'}));
			const review = screen.getByRole('dialog', {name: 'Apply Modifications'});
			await expect.element(review.getByRole('button', {name: 'Apply'})).toBeEnabled();

			worker.use(
				mockQueryProcessDefinitionsEndpoint({
					successResponse: HttpResponse.json(
						createQueryProcessDefinitionsResponse({
							items: ['tenant-a', 'tenant-b'].map((tenantId, index) =>
								createProcessDefinition({
									processDefinitionId: 'my_simple_process',
									processDefinitionKey: String(123 + index),
									tenantId,
									version: 1,
									name: 'Invoice process',
								}),
							),
						}),
					),
				}),
			);
			await screen.queryClient.invalidateQueries();

			await expect.element(screen.getByText('Process "Invoice process" exists in more than one Tenant')).toBeVisible();
			await expect.element(review.getByRole('button', {name: 'Apply'})).toBeDisabled();
			await userEvent.click(review.getByRole('button', {name: 'Apply'}), {force: true});
			await expect.element(screen.getByText('Batch Modification Mode')).toBeVisible();
			expect(modificationRequests).toEqual([]);
		} finally {
			worker.events.removeListener('request:start', onRequest);
		}
	});

	it('should leave the affected instances blank while counting', async ({worker}) => {
		mockPage(worker);
		worker.use(mockGetProcessDefinitionStatisticsEndpoint({successResponse: statistics(2, 1), delay: 'infinite'}));
		const screen = await renderPage();
		await enterMode(screen);
		await clickDiagramElement('end_event');

		await userEvent.click(screen.getByRole('button', {name: 'Review Modification'}));

		const review = screen.getByRole('dialog', {name: 'Apply Modifications'});
		const row = review.getByRole('row').filter({has: page.getByRole('cell', {name: 'Batch move'})});
		await expect.element(row.getByRole('cell').nth(2)).toBeEmptyDOMElement();
		await expect.element(review.getByRole('button', {name: 'Apply'})).toBeEnabled();
	});

	it('should exit the mode as soon as the move is applied and lock the operations until it is accepted', async ({
		worker,
	}) => {
		mockPage(worker);
		worker.use(mockCreateModificationBatchOperationEndpoint({successResponse: accepted(), delay: 'infinite'}));
		const screen = await renderPage();
		await enterMode(screen);
		await clickDiagramElement('end_event');
		await userEvent.click(screen.getByRole('button', {name: 'Review Modification'}));

		await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Apply'}));

		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
		await expect.element(screen.getByText('Batch Modification Mode')).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Move'})).toBeDisabled();
		await expect.element(screen.getByRole('button', {name: 'Cancel', exact: true})).toBeDisabled();
		await selectInstance(screen, '2');
		await expect.element(screen.getByRole('checkbox', {name: 'Select instance 2'})).not.toBeChecked();
	});

	it.for([
		{scope: 'selected', processInstanceKey: z.strictObject({$in: z.tuple([z.literal('1')])})},
		{scope: 'all', processInstanceKey: z.undefined().optional()},
		{scope: 'all but excluded', processInstanceKey: z.strictObject({$notIn: z.tuple([z.literal('2')])})},
	])('should move the $scope instances and keep the selection', async ({scope, processInstanceKey}, {worker}) => {
		mockPage(worker);
		worker.use(
			mockCreateModificationBatchOperationEndpoint({
				schema: z.strictObject({
					filter: z.looseObject({
						processDefinitionId: z.strictObject({$eq: z.literal('my_simple_process')}),
						processDefinitionVersion: z.literal(1),
						tenantId: z.strictObject({$eq: z.literal('tenant-a')}),
						state: z.strictObject({$eq: z.literal('ACTIVE')}),
						processInstanceKey,
					}),
					moveInstructions: z.tuple([
						z.strictObject({sourceElementId: z.literal('task-1'), targetElementId: z.literal('end_event')}),
					]),
				}),
				successResponse: accepted(),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(
					createBatchOperation({batchOperationKey: 'batch-1', batchOperationType: 'MODIFY_PROCESS_INSTANCE'}),
				),
			}),
		);
		storeStateLocally(HELPER_STORAGE_KEY, true);
		const screen = await renderPage();
		if (scope === 'selected') {
			await selectInstance(screen, '1');
		} else {
			await userEvent.click(screen.getByRole('checkbox', {name: 'Select all items'}), {force: true});
		}
		if (scope === 'all but excluded') {
			await selectInstance(screen, '2');
		}
		await userEvent.click(screen.getByRole('button', {name: 'Move'}));
		await clickDiagramElement('end_event');
		await userEvent.click(screen.getByRole('button', {name: 'Review Modification'}));

		await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Apply'}));

		await expect
			.element(screen.getByText('The batch operation "Modify Process Instance" has been started'))
			.toBeVisible();
		await expect.element(screen.getByText('Batch Modification Mode')).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Move'})).toBeEnabled();
	});
});
