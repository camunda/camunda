/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {http, HttpResponse} from 'msw';
import type {SetupWorker} from 'msw/browser';
import {z} from 'zod';
import {
	endpoints,
	type ProcessDefinition,
	type QueryProcessDefinitionsRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockCreateMigrationBatchOperationEndpoint,
	mockCurrentUserEndpoint,
	mockGetProcessDefinitionStatisticsEndpoint,
	mockGetProcessDefinitionXmlByKeyEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockQueryProcessDefinitionsByFilterEndpoint,
	mockCreateCancellationBatchOperationEndpoint,
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
import {
	BPMN_XML,
	FLIGHT_REGISTRATION_BPMN_XML,
	MIGRATION_SOURCE_BPMN_XML,
	UPDATED_BPMN_XML,
} from '#/shared-test-modules/api-mocks/process-definition-xmls';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {createQueryBatchOperationItemsResponse} from '#/shared-test-modules/api-mocks/batch-operations';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {Notifications} from '#/shared/notifications/components/Notifications';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {ProcessesHarness} from './ProcessesHarness';
import {getInstanceMigrationLocation} from './instanceMigration';

const SOURCE = createProcessDefinition({
	processDefinitionId: 'my_simple_process',
	processDefinitionKey: 'source-key',
	name: 'Invoice process',
	version: 1,
	tenantId: 'tenant-a',
});
const PREVIOUS_TARGET = createProcessDefinition({
	processDefinitionId: 'my_simple_process',
	processDefinitionKey: 'previous-key',
	name: 'Invoice process',
	version: 2,
	tenantId: 'tenant-a',
});
const LATEST_TARGET = createProcessDefinition({
	processDefinitionId: 'my_simple_process',
	processDefinitionKey: 'latest-key',
	name: 'Invoice process',
	version: 3,
	tenantId: 'tenant-a',
});
const FLIGHT_REGISTRATION = createProcessDefinition({
	processDefinitionId: 'flightRegistration',
	processDefinitionKey: 'flight-key',
	name: 'Flight registration',
	version: 1,
	tenantId: 'tenant-a',
});
const SEARCH = 'process=my_simple_process&version=1&tenantId=tenant-a';
const XML_BY_KEY = {
	'source-key': MIGRATION_SOURCE_BPMN_XML,
	'previous-key': UPDATED_BPMN_XML,
	'latest-key': BPMN_XML,
	'flight-key': FLIGHT_REGISTRATION_BPMN_XML,
};

function getProcessDefinitionId(filter: QueryProcessDefinitionsRequestBody['filter']) {
	const processDefinitionId = filter?.processDefinitionId;
	return typeof processDefinitionId === 'object' ? processDefinitionId.$eq : processDefinitionId;
}

function createListResponse(hasMoreTotalItems: boolean) {
	return createQueryProcessInstancesResponse({
		items: [
			createProcessInstance({processInstanceKey: '1', state: 'ACTIVE'}),
			createProcessInstance({processInstanceKey: '2', state: 'ACTIVE'}),
			createProcessInstance({processInstanceKey: '3', state: 'COMPLETED'}),
		],
		page: {totalItems: 3, hasMoreTotalItems},
	});
}

function mockPage(
	worker: SetupWorker,
	{
		sourceVersions = [SOURCE, PREVIOUS_TARGET, LATEST_TARGET],
		latestOtherProcesses = [FLIGHT_REGISTRATION],
		xmlByKey = XML_BY_KEY,
		hasMoreTotalItems = false,
	}: {
		sourceVersions?: ProcessDefinition[];
		latestOtherProcesses?: ProcessDefinition[];
		xmlByKey?: Record<string, string>;
		hasMoreTotalItems?: boolean;
	} = {},
) {
	worker.use(
		mockQueryProcessDefinitionsByFilterEndpoint({
			getResponse: (filter) => {
				const items = filter?.isLatestVersion
					? latestOtherProcesses
					: getProcessDefinitionId(filter) === FLIGHT_REGISTRATION.processDefinitionId
						? [FLIGHT_REGISTRATION]
						: sourceVersions;
				return HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: items.filter(({state}) => filter?.state === undefined || state === filter.state),
					}),
				);
			},
		}),
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(createListResponse(hasMoreTotalItems)),
		}),
		mockQueryBatchOperationItemsEndpoint({
			successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
		}),
		mockGetProcessDefinitionXmlByKeyEndpoint({xmlByKey}),
		mockGetProcessDefinitionStatisticsEndpoint({
			successResponse: HttpResponse.json(
				createGetProcessDefinitionStatisticsResponse([
					createProcessDefinitionStatistic({elementId: 'task-1', active: 2, incidents: 1}),
					createProcessDefinitionStatistic({elementId: 'check-payment', active: 4}),
				]),
			),
		}),
	);
}

function renderPage(search = SEARCH) {
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

async function enterMigration(screen: Screen) {
	await selectInstance(screen, '1');
	await userEvent.click(screen.getByRole('button', {name: 'Migrate'}));
	await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Continue'}), {force: true});
	await expect.element(screen.getByText('Migration step 1 - mapping elements')).toBeVisible();
}

async function enterSummary(screen: Screen) {
	await enterMigration(screen);
	await userEvent.click(screen.getByRole('button', {name: 'Next'}));
	await expect.element(screen.getByText('Migration step 2 - confirm')).toBeVisible();
}

async function confirmMigration(screen: Screen) {
	await userEvent.click(screen.getByRole('button', {name: 'Confirm'}));
	const dialog = screen.getByRole('dialog', {name: 'Migration confirmation'});
	await userEvent.fill(dialog.getByRole('textbox', {name: 'Type MIGRATE to confirm'}), 'MIGRATE');
	await userEvent.click(dialog.getByRole('button', {name: 'Confirm'}));
}

const accepted = () =>
	HttpResponse.json({batchOperationKey: 'batch-1', batchOperationType: 'MIGRATE_PROCESS_INSTANCE'}, {status: 202});

function getMappingRow(screen: Screen, name: string) {
	return screen.getByRole('row').filter({hasText: name});
}

async function clickTargetDiagramElement(screen: Screen, elementId: string) {
	const targetDiagram = screen.getByTestId('diagram-body').nth(1);
	await expect.poll(() => targetDiagram.element().querySelector(`[data-element-id="${elementId}"]`)).not.toBeNull();
	await userEvent.click(targetDiagram.element().querySelector<SVGElement>(`[data-element-id="${elementId}"]`)!);
}

describe('Processes migration', () => {
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
			condition: 'no process version is selected',
			search: 'process=my_simple_process&tenantId=tenant-a',
			instanceKey: '1',
			xmlByKey: XML_BY_KEY,
			reason: 'To start the migration process, choose a process and version first.',
		},
		{
			condition: 'the source diagram cannot be fetched',
			search: SEARCH,
			instanceKey: '1',
			xmlByKey: {},
			reason: 'Issue fetching diagram, contact admin if problem persists.',
		},
		{
			condition: 'no running instance is selected',
			search: SEARCH,
			instanceKey: '3',
			xmlByKey: XML_BY_KEY,
			reason: 'You can only migrate instances in active or incident state.',
		},
	])('should disable Migrate when $condition', async ({search, instanceKey, xmlByKey, reason}, {worker}) => {
		mockPage(worker, {xmlByKey});
		const screen = await renderPage(search);

		await selectInstance(screen, instanceKey);

		await expect.element(screen.getByRole('button', {name: 'Migrate'})).toBeDisabled();
		await expect.element(screen.getByRole('button', {name: 'Migrate'})).toHaveAttribute('title', reason);
	});

	it('should disable Migrate in batch modification mode', async ({worker}) => {
		mockPage(worker);
		storeStateLocally('operate.hideMoveModificationHelperModal', true);
		const screen = await renderPage(`${SEARCH}&elementId=task-1`);
		await selectInstance(screen, '1');

		await userEvent.click(screen.getByRole('button', {name: 'Move'}));

		await expect.element(screen.getByText('Batch Modification Mode')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Migrate'})).toBeDisabled();
		await expect
			.element(screen.getByRole('button', {name: 'Migrate'}))
			.toHaveAttribute('title', 'Not available in batch modification mode');
	});

	it('should disable Migrate while another operation is being submitted', async ({worker}) => {
		mockPage(worker);
		worker.use(
			mockCreateCancellationBatchOperationEndpoint({
				successResponse: HttpResponse.json({
					batchOperationKey: 'batch-1',
					batchOperationType: 'CANCEL_PROCESS_INSTANCE',
				}),
				delay: 'infinite',
			}),
		);
		const screen = await renderPage();
		await selectInstance(screen, '1');
		await expect.element(screen.getByRole('button', {name: 'Migrate'})).toBeEnabled();

		await userEvent.click(screen.getByRole('button', {name: 'Cancel', exact: true}));
		await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Apply'}));

		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Migrate'})).toBeDisabled();
	});

	it('should explain migration in the helper before entering the mapping step', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await selectInstance(screen, '1');

		await userEvent.click(screen.getByRole('button', {name: 'Migrate'}));

		const helper = screen.getByRole('dialog', {name: 'Migrate process instance versions'});
		await expect
			.element(helper.getByRole('listitem').nth(0))
			.toHaveTextContent('Migrate is used to migrate running process instances to a different process definition.');
		await expect
			.element(helper.getByRole('listitem').nth(1))
			.toHaveTextContent(
				'When the migration steps are executed, all selected process instances will be affected. This can lead to interruptions, delays or changes.',
			);
		await expect
			.element(helper.getByRole('listitem').nth(2))
			.toHaveTextContent(
				'To minimize interruptions or delays, plan the migration at times when the system load is low.',
			);
		await expect
			.element(helper.getByRole('link', {name: 'migration documentation'}))
			.toHaveAttribute('href', 'https://docs.camunda.io/docs/components/operate/userguide/process-instance-migration/');

		await userEvent.click(helper.getByRole('button', {name: 'Cancel'}));

		await expect.element(helper).not.toBeInTheDocument();
		await expect.element(screen.getByText('Migration step 1 - mapping elements')).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('button', {name: 'Migrate'}));
		await userEvent.click(helper.getByRole('button', {name: 'Continue'}), {force: true});

		await expect.element(screen.getByText('Migration step 1 - mapping elements')).toBeVisible();
		await expect
			.element(screen.getByRole('heading', {name: 'Operate Process Instances - Migration Mode'}))
			.toBeInTheDocument();
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).not.toBeInTheDocument();
	});

	it('should skip the helper when legacy Operate hid it', async ({worker}) => {
		mockPage(worker);
		localStorage.setItem('sharedState', JSON.stringify({hideMigrationHelperModal: true}));
		const screen = await renderPage();
		await selectInstance(screen, '1');

		await userEvent.click(screen.getByRole('button', {name: 'Migrate'}));

		await expect.element(screen.getByText('Migration step 1 - mapping elements')).toBeVisible();
		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
	});

	it('should not offer migration when the selected states exclude active instances', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage(`${SEARCH}&active=false&incidents=false&suspended=true`);
		await selectInstance(screen, '1');

		await expect.element(screen.getByRole('button', {name: 'Migrate'})).toBeDisabled();
		await expect
			.element(screen.getByRole('button', {name: 'Migrate'}))
			.toHaveAttribute('title', 'You can only migrate instances in active or incident state.');
	});

	it('should skip the helper after choosing not to show it again', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await selectInstance(screen, '1');
		await userEvent.click(screen.getByRole('button', {name: 'Migrate'}));

		await userEvent.click(screen.getByRole('checkbox', {name: "Don't show this message next time"}), {force: true});
		await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Continue'}), {force: true});

		await expect.element(screen.getByText('Migration step 1 - mapping elements')).toBeVisible();
		expect(getStateLocally('operate.hideMigrationHelperModal')).toBe(true);

		await userEvent.click(screen.getByRole('button', {name: 'Exit migration'}));
		await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Exit'}));
		await selectInstance(screen, '1');
		await userEvent.click(screen.getByRole('button', {name: 'Migrate'}));

		await expect.element(screen.getByText('Migration step 1 - mapping elements')).toBeVisible();
		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
	});

	it('should preselect the latest other version and automatically map matching elements', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();

		await enterMigration(screen);

		await expect.element(screen.getByText('Source', {exact: true})).toBeVisible();
		await expect.element(screen.getByText('Invoice process', {exact: true})).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Target'})).toHaveValue('Invoice process');
		await expect.element(screen.getByRole('combobox', {name: 'Target Version'})).toHaveTextContent('3Open menu');

		const reviewRow = getMappingRow(screen, 'Review invoice');
		await expect.element(reviewRow.getByRole('combobox')).toHaveValue('task-1');
		await expect.element(reviewRow.getByTitle('This element was automatically mapped')).toBeVisible();
		await expect.element(reviewRow.getByText('Not mapped')).not.toBeInTheDocument();

		const checkPaymentRow = getMappingRow(screen, 'Check payment');
		await expect.element(checkPaymentRow.getByRole('combobox')).toBeDisabled();
		await expect.element(checkPaymentRow.getByText('Not mapped')).toBeVisible();

		await expect
			.element(
				screen.getByText(
					'Embedded forms in the source user tasks will be replaced by the form defined in the target element.',
				),
			)
			.toBeVisible();
		await expect
			.element(screen.getByRole('link', {name: 'Learn more about migration of user tasks with embedded forms'}))
			.toHaveAttribute(
				'href',
				'https://docs.camunda.io/docs/components/concepts/process-instance-migration/#migrate-job-worker-user-tasks-to-camunda-user-tasks',
			);
		await expect
			.element(screen.getByRole('link', {name: 'Learn more about migration of user tasks with embedded forms'}))
			.toHaveAccessibleDescription(
				'Embedded forms in the source user tasks will be replaced by the form defined in the target element.',
			);
	});

	it('should not offer a version that is being deleted as the migration target', async ({worker}) => {
		mockPage(worker, {
			sourceVersions: [
				SOURCE,
				PREVIOUS_TARGET,
				LATEST_TARGET,
				{...LATEST_TARGET, processDefinitionKey: 'draining-key', version: 4, state: 'DRAINING'},
			],
		});
		const screen = await renderPage();

		await enterMigration(screen);

		await expect.element(screen.getByRole('combobox', {name: 'Target Version'})).toHaveTextContent('3Open menu');
		await userEvent.click(screen.getByRole('combobox', {name: 'Target Version'}));
		await expect.element(screen.getByRole('option', {name: '2'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: '4'})).not.toBeInTheDocument();
	});

	it('should replace the mapping with the automatic mapping of a newly selected target', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterMigration(screen);
		const reviewSelect = getMappingRow(screen, 'Review invoice').getByRole('combobox');
		await expect.element(reviewSelect).toHaveValue('task-1');

		await userEvent.click(screen.getByRole('combobox', {name: 'Target Version'}));
		await userEvent.click(screen.getByRole('option', {name: '2'}));

		await expect.element(reviewSelect).toHaveValue('');
		await expect.element(getMappingRow(screen, 'Review invoice').getByText('Not mapped')).toBeVisible();

		await userEvent.selectOptions(reviewSelect, 'task-2');

		await expect.element(reviewSelect).toHaveValue('task-2');
		await expect
			.element(getMappingRow(screen, 'Review invoice').getByTitle('This element was automatically mapped'))
			.not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('combobox', {name: 'Target Version'}));
		await userEvent.click(screen.getByRole('option', {name: '3'}));

		await expect.element(reviewSelect).toHaveValue('task-1');

		await userEvent.fill(screen.getByRole('combobox', {name: 'Target'}), 'Flight');
		await userEvent.click(screen.getByRole('option', {name: 'Flight registration'}));

		await expect.element(screen.getByRole('combobox', {name: 'Target Version'})).toHaveTextContent('1Open menu');
		await expect.element(reviewSelect).toHaveValue('');
		await expect
			.element(reviewSelect)
			.toHaveTextContent(
				[
					'Register the passenger',
					'Register cabin bag',
					'Determine luggage weight',
					'Print out boarding pass',
					'Register the luggage',
					'Process payment',
				].join(''),
			);
	});

	it('should show only elements that are not mapped when the filter is on', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterMigration(screen);
		await expect.element(getMappingRow(screen, 'Review invoice')).toBeVisible();

		await userEvent.click(screen.getByRole('switch', {name: 'Show only not mapped'}), {force: true});

		await expect.element(getMappingRow(screen, 'Check payment')).toBeVisible();
		await expect.element(getMappingRow(screen, 'Review invoice')).not.toBeInTheDocument();
	});

	it('should keep listing only the not mapped elements on the summary, like legacy', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterMigration(screen);
		await userEvent.click(screen.getByRole('switch', {name: 'Show only not mapped'}), {force: true});
		await expect.element(getMappingRow(screen, 'Review invoice')).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('button', {name: 'Next'}));

		await expect.element(screen.getByText('Migration step 2 - confirm')).toBeVisible();
		await expect.element(getMappingRow(screen, 'Review invoice')).not.toBeInTheDocument();
		await expect.element(screen.getByRole('switch', {name: 'Show only not mapped'})).toBeEnabled();
	});

	it('should select the source elements that are mapped to a selected element', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterMigration(screen);
		const reviewRow = getMappingRow(screen, 'Review invoice');
		const checkPaymentRow = getMappingRow(screen, 'Check payment');
		await expect.element(reviewRow).toHaveAttribute('aria-selected', 'false');

		await clickTargetDiagramElement(screen, 'task-1');

		await expect.element(reviewRow).toHaveAttribute('aria-selected', 'true');
		await expect.element(checkPaymentRow).toHaveAttribute('aria-selected', 'false');

		await userEvent.click(checkPaymentRow.getByText('Check payment'));

		await expect.element(checkPaymentRow).toHaveAttribute('aria-selected', 'true');
		await expect.element(reviewRow).toHaveAttribute('aria-selected', 'false');

		await userEvent.click(checkPaymentRow.getByText('Check payment'));

		await expect.element(checkPaymentRow).toHaveAttribute('aria-selected', 'false');
	});

	it('should not change the selected row when operating its target select', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterMigration(screen);
		const reviewRow = getMappingRow(screen, 'Review invoice');
		const reviewSelect = reviewRow.getByRole('combobox');

		await userEvent.click(reviewSelect);

		await expect.element(reviewRow).toHaveAttribute('aria-selected', 'false');

		await userEvent.keyboard('{Enter}');

		await expect.element(reviewRow).toHaveAttribute('aria-selected', 'false');

		await userEvent.selectOptions(reviewSelect, '');

		await expect.element(reviewSelect).toHaveValue('');
		await expect.element(reviewRow).toHaveAttribute('aria-selected', 'false');

		reviewRow.element().focus();
		await userEvent.keyboard('{Enter}');

		await expect.element(reviewRow).toHaveAttribute('aria-selected', 'true');
	});

	it('should ask for a target when the source process has no other version', async ({worker}) => {
		mockPage(worker, {sourceVersions: [SOURCE], latestOtherProcesses: []});
		const screen = await renderPage();

		await enterMigration(screen);

		await expect.element(screen.getByText('Select a target process and version')).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Target'})).toBeDisabled();
		await expect.element(screen.getByRole('combobox', {name: 'Target Version'})).toBeDisabled();
		await expect.element(screen.getByRole('columnheader', {name: 'Source elements'})).toBeVisible();
		await expect.element(screen.getByRole('switch', {name: 'Show only not mapped'})).not.toBeInTheDocument();
		await expect
			.element(screen.getByRole('combobox', {name: 'Target element for Review invoice'}))
			.not.toBeInTheDocument();
	});

	it('should explain when the source process has no mappable elements', async ({worker}) => {
		mockPage(worker, {xmlByKey: {...XML_BY_KEY, 'source-key': FLIGHT_REGISTRATION_BPMN_XML}});
		const screen = await renderPage();

		await enterMigration(screen);

		await expect.element(screen.getByText('There are no mappable elements or sequence flows.')).toBeVisible();
		await expect.element(screen.getByText('Exit migration to select a different process')).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: 'Source elements'})).not.toBeInTheDocument();
	});

	it('should exit after confirmation and keep the list filters', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterMigration(screen);

		await userEvent.click(screen.getByRole('button', {name: 'Exit migration'}));

		const dialog = screen.getByRole('dialog', {name: 'Exit migration'});
		await expect
			.element(dialog)
			.toMatchTextContent(
				'You are about to leave ongoing migration, all planned mapping/s will be discarded.Click “Exit” to proceed.',
			);

		await userEvent.click(dialog.getByRole('button', {name: 'Cancel'}));

		await expect.element(dialog).not.toBeInTheDocument();
		await expect.element(screen.getByText('Migration step 1 - mapping elements')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Exit migration'}));
		await userEvent.click(dialog.getByRole('button', {name: 'Exit'}));

		await expect.element(screen.getByText('Migration step 1 - mapping elements')).not.toBeInTheDocument();
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('Invoice process');
		await expect.element(screen.getByRole('checkbox', {name: 'Select instance 1'})).not.toBeChecked();
		expect(screen.router.state.location.searchStr).toBe(`?${SEARCH}`);
	});

	it('should ask before navigating away from the mapping step', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterMigration(screen);

		screen.router.history.push('/operate/processes?process=my_simple_process');

		const dialog = screen.getByRole('dialog', {name: 'Leave Migration Mode'});
		await expect
			.element(dialog.getByText('By leaving this page, all planned mapping/s will be discarded.'))
			.toBeVisible();

		await userEvent.click(dialog.getByRole('button', {name: 'Stay'}));

		await expect.element(dialog).not.toBeInTheDocument();
		await expect.element(screen.getByText('Migration step 1 - mapping elements')).toBeVisible();
		expect(screen.router.state.location.searchStr).toBe(`?${SEARCH}`);

		screen.router.history.push('/operate/processes?process=my_simple_process');
		await userEvent.click(dialog.getByRole('button', {name: 'Leave'}));

		await expect.element(screen.getByText('Migration step 1 - mapping elements')).not.toBeInTheDocument();
		await expect.poll(() => screen.router.state.location.searchStr).toBe('?process=my_simple_process');
	});

	it('should require at least one mapped element before the summary', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterMigration(screen);
		const reviewSelect = getMappingRow(screen, 'Review invoice').getByRole('combobox');
		await expect.element(screen.getByRole('button', {name: 'Next'})).toBeEnabled();

		await userEvent.selectOptions(reviewSelect, '');

		await expect.element(screen.getByRole('button', {name: 'Next'})).toBeDisabled();
		await expect
			.element(screen.getByRole('button', {name: 'Next'}))
			.toHaveAttribute('title', 'Please map at least one element to continue');

		await userEvent.selectOptions(reviewSelect, 'task-1');

		await expect.element(screen.getByRole('button', {name: 'Next'})).toBeEnabled();
		await expect.element(screen.getByRole('button', {name: 'Next'})).not.toHaveAttribute('title');
	});

	it('should summarize the planned migration and keep the mapping when going back', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();

		await enterSummary(screen);

		await expect
			.element(screen.getByText(/^You are about to migrate/))
			.toHaveTextContent(
				'You are about to migrate 1 process instance from the process definition: Invoice process - version 1 to the process definition: Invoice process - version 3',
			);
		await expect
			.element(
				screen.getByText(
					'This process can take several minutes until it completes. You can observe progress of this in the operations panel.',
				),
			)
			.toBeVisible();
		await expect
			.element(
				screen.getByText(
					'The elements and sequence flows listed below will be mapped from the source on the left side to target on the right side.',
				),
			)
			.toBeVisible();
		await expect.element(screen.getByText('Source', {exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('combobox', {name: 'Target'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('combobox', {name: 'Target Version'})).not.toBeInTheDocument();
		await expect.element(getMappingRow(screen, 'Review invoice').getByRole('combobox')).toBeDisabled();
		await expect.element(screen.getByTestId('state-overlay-task-1-active')).toHaveTextContent('2');
		await expect.element(screen.getByTestId('state-overlay-task-1-incidents')).toHaveTextContent('1');
		await expect.element(screen.getByTestId('state-overlay-check-payment-active')).toHaveTextContent('4');
		await expect.element(screen.getByTestId('modifications-overlay')).toHaveTextContent('3');

		await userEvent.click(screen.getByRole('button', {name: 'Back'}));

		await expect.element(screen.getByText('Migration step 1 - mapping elements')).toBeVisible();
		await expect.element(screen.getByText(/^You are about to migrate/)).not.toBeInTheDocument();
		await expect.element(screen.getByRole('combobox', {name: 'Target Version'})).toHaveTextContent('3Open menu');
		await expect.element(getMappingRow(screen, 'Review invoice').getByRole('combobox')).toHaveValue('task-1');
		await expect.element(getMappingRow(screen, 'Review invoice').getByRole('combobox')).toBeEnabled();
		await expect.element(screen.getByTestId('state-overlay-task-1-active')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('modifications-overlay')).not.toBeInTheDocument();
	});

	it('should show where the instances are when the summary opens', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterMigration(screen);
		worker.use(
			mockGetProcessDefinitionStatisticsEndpoint({
				successResponse: HttpResponse.json(
					createGetProcessDefinitionStatisticsResponse([
						createProcessDefinitionStatistic({elementId: 'check-payment', active: 5}),
					]),
				),
			}),
		);

		await userEvent.click(screen.getByRole('button', {name: 'Next'}));

		await expect.element(screen.getByTestId('state-overlay-check-payment-active')).toHaveTextContent('5');
		await expect.element(screen.getByTestId('state-overlay-task-1-active')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('modifications-overlay')).not.toBeInTheDocument();
	});

	it('should hide stale overlays while the summary refreshes where the instances are', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterSummary(screen);
		await expect.element(screen.getByTestId('state-overlay-task-1-active')).toHaveTextContent('2');
		await userEvent.click(screen.getByRole('button', {name: 'Back'}));
		worker.use(
			mockGetProcessDefinitionStatisticsEndpoint({
				delay: 'infinite',
				successResponse: HttpResponse.json(createGetProcessDefinitionStatisticsResponse([])),
			}),
		);

		await userEvent.click(screen.getByRole('button', {name: 'Next'}));

		await expect.element(screen.getByText('Migration step 2 - confirm')).toBeVisible();
		await expect.element(screen.getByTestId('state-overlay-task-1-active')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('modifications-overlay')).not.toBeInTheDocument();
	});

	it('should hide stale overlays when the summary cannot refresh where the instances are', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterSummary(screen);
		await expect.element(screen.getByTestId('state-overlay-task-1-active')).toHaveTextContent('2');
		await userEvent.click(screen.getByRole('button', {name: 'Back'}));
		let reportStatisticsFailure = () => {};
		const statisticsFailed = new Promise<void>((resolve) => {
			reportStatisticsFailure = resolve;
		});
		worker.use(
			http.post(
				endpoints.getProcessDefinitionStatistics.getUrl({
					processDefinitionKey: ':processDefinitionKey',
					statisticName: 'element-instances',
				}),
				() => {
					reportStatisticsFailure();
					return new HttpResponse(null, {status: 500});
				},
			),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Next'}));
		await statisticsFailed;

		await expect.element(screen.getByText(/^You are about to migrate 1 process instance /)).toBeVisible();
		await expect.element(screen.getByTestId('state-overlay-task-1-active')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('modifications-overlay')).not.toBeInTheDocument();
	});

	it.for([
		{selection: 'one instance', keys: ['1'], hasMoreTotalItems: false, instances: '1 process instance'},
		{selection: 'two instances', keys: ['1', '2'], hasMoreTotalItems: false, instances: '2 process instances'},
		{
			selection: 'one running and one completed instance',
			keys: ['1', '3'],
			hasMoreTotalItems: false,
			instances: '2 process instances',
		},
		{selection: 'all of a truncated list', keys: [], hasMoreTotalItems: true, instances: '3+ process instances'},
		{
			selection: 'all of a list with a completed instance',
			keys: [],
			hasMoreTotalItems: false,
			instances: '3 process instances',
		},
	])(
		'should state the number of instances when migrating $selection',
		async ({keys, hasMoreTotalItems, instances}, {worker}) => {
			mockPage(worker, {hasMoreTotalItems});
			storeStateLocally('operate.hideMigrationHelperModal', true);
			const screen = await renderPage();
			if (keys.length === 0) {
				await userEvent.click(screen.getByRole('checkbox', {name: 'Select all items'}), {force: true});
			}
			for (const key of keys) {
				await selectInstance(screen, key);
			}
			await userEvent.click(screen.getByRole('button', {name: 'Migrate'}));
			await userEvent.click(screen.getByRole('button', {name: 'Next'}));

			await userEvent.click(screen.getByRole('button', {name: 'Confirm'}));

			await expect
				.element(screen.getByRole('dialog', {name: 'Migration confirmation'}).getByText(/^You are about to migrate/))
				.toHaveTextContent(
					`You are about to migrate ${instances} from the process definition: Invoice process - version 1 to the process definition: Invoice process - version 3`,
				);
		},
	);

	it('should show process names verbatim in the migration details', async ({worker}) => {
		const name = 'Order <A> & B';
		mockPage(worker, {
			sourceVersions: [SOURCE, PREVIOUS_TARGET, LATEST_TARGET].map((definition) => ({...definition, name})),
		});
		const screen = await renderPage();

		await enterSummary(screen);

		await expect
			.element(screen.getByText(/^You are about to migrate/))
			.toHaveTextContent(
				`You are about to migrate 1 process instance from the process definition: ${name} - version 1 to the process definition: ${name} - version 3`,
			);
	});

	it('should only submit after typing MIGRATE and discard the input when cancelled', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterSummary(screen);
		await userEvent.click(screen.getByRole('button', {name: 'Confirm'}));
		const dialog = screen.getByRole('dialog', {name: 'Migration confirmation'});
		const input = dialog.getByRole('textbox', {name: 'Type MIGRATE to confirm'});
		await expect.element(dialog.getByRole('button', {name: 'Confirm'})).toBeDisabled();
		await expect.element(dialog.getByText('Value must match MIGRATE')).not.toBeInTheDocument();

		await userEvent.fill(input, 'MIGRAT');

		await expect.element(dialog.getByText('Value must match MIGRATE')).toBeVisible();
		await expect.element(dialog.getByRole('button', {name: 'Confirm'})).toBeDisabled();

		await userEvent.fill(input, 'MIGRATE');

		await expect.element(dialog.getByText('Value must match MIGRATE')).not.toBeInTheDocument();
		await expect.element(dialog.getByRole('button', {name: 'Confirm'})).toBeEnabled();

		await userEvent.click(dialog.getByRole('button', {name: 'Cancel'}));
		await expect.element(dialog).not.toBeInTheDocument();
		await userEvent.click(screen.getByRole('button', {name: 'Confirm'}));

		await expect.element(input).toHaveValue('');
		await expect.element(dialog.getByRole('button', {name: 'Confirm'})).toBeDisabled();
	});

	it.for([
		{multiTenancy: 'disabled', isMultiTenancyEnabled: false, tenantId: undefined},
		{multiTenancy: 'enabled', isMultiTenancyEnabled: true, tenantId: 'tenant-a'},
	])(
		'should migrate the selected instances and list the target with multi-tenancy $multiTenancy',
		async ({isMultiTenancyEnabled, tenantId}, {worker}) => {
			sessionStorage.setItem(
				'clientConfig',
				JSON.stringify(
					createSystemConfiguration({
						deployment: {...createSystemConfiguration().deployment, isMultiTenancyEnabled},
					}),
				),
			);
			mockPage(worker);
			worker.use(
				mockCurrentUserEndpoint({
					successResponse: HttpResponse.json(
						createCurrentUser({tenants: [{tenantId: 'tenant-a', name: 'Tenant A', description: null}]}),
					),
				}),
				mockCreateMigrationBatchOperationEndpoint({
					schema: z.strictObject({
						filter: z.strictObject({
							$or: z.tuple([
								z.strictObject({state: z.strictObject({$eq: z.literal('ACTIVE')}), hasIncident: z.literal(false)}),
								z.strictObject({state: z.strictObject({$eq: z.literal('SUSPENDED')})}),
								z.strictObject({
									hasIncident: z.literal(true),
									state: z.strictObject({$neq: z.literal('SUSPENDED')}),
								}),
							]),
							state: z.strictObject({$eq: z.literal('ACTIVE')}),
							processDefinitionId: z.strictObject({$eq: z.literal('my_simple_process')}),
							processDefinitionVersion: z.literal(1),
							tenantId: z.strictObject({$eq: z.literal('tenant-a')}),
							processInstanceKey: z.strictObject({$in: z.tuple([z.literal('1')])}),
							processDefinitionKey: z.strictObject({$eq: z.literal('source-key')}),
						}),
						migrationPlan: z.strictObject({
							targetProcessDefinitionKey: z.literal('latest-key'),
							mappingInstructions: z.tuple([
								z.strictObject({sourceElementId: z.literal('task-1'), targetElementId: z.literal('task-1')}),
							]),
						}),
					}),
					successResponse: accepted(),
					failureResponse: new HttpResponse(null, {status: 400}),
				}),
			);
			const screen = await renderPage();
			await enterSummary(screen);

			await confirmMigration(screen);

			await expect
				.element(screen.getByText('The batch operation "Migrate Process Instance" has been started'))
				.toBeVisible();
			await expect.element(screen.getByText('You can track its progress in the Batch Operation page')).toBeVisible();
			await expect.element(screen.getByText('Migration step 2 - confirm')).not.toBeInTheDocument();
			await expect.element(screen.getByRole('checkbox', {name: 'Select instance 1'})).not.toBeChecked();
			expect(screen.router.state.location.search).toEqual({
				active: true,
				suspended: true,
				incidents: true,
				process: 'my_simple_process',
				version: 3,
				tenantId,
			});

			await userEvent.click(screen.getByRole('button', {name: 'Go to operation details'}));

			await expect.poll(() => screen.router.state.location.pathname).toBe('/operate/batch-operations/batch-1');
		},
	);

	it('should not return to the target list when the user left while the migration was pending', async ({worker}) => {
		mockPage(worker);
		worker.use(mockCreateMigrationBatchOperationEndpoint({successResponse: accepted(), delay: 1500}));
		const screen = await renderPage();
		await enterSummary(screen);
		await confirmMigration(screen);
		await expect.element(screen.getByText('Migrating...')).toBeVisible();

		screen.router.history.push('/operate/processes?process=my_simple_process');
		await userEvent.click(
			screen.getByRole('dialog', {name: 'Leave Migration Mode'}).getByRole('button', {name: 'Leave'}),
		);

		await expect
			.element(screen.getByText('The batch operation "Migrate Process Instance" has been started'), {timeout: 4000})
			.toBeVisible();
		expect(screen.router.state.location.searchStr).toBe('?process=my_simple_process');
	});

	it('should show the pending migration and lock the footer until it is accepted', async ({worker}) => {
		mockPage(worker);
		worker.use(mockCreateMigrationBatchOperationEndpoint({successResponse: accepted(), delay: 'infinite'}));
		const screen = await renderPage();
		await enterSummary(screen);

		await confirmMigration(screen);

		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
		await expect.element(screen.getByText('Migrating...')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Confirm'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Back'})).toBeDisabled();
		await expect.element(screen.getByRole('button', {name: 'Exit migration'})).toBeDisabled();
	});

	it.for([
		{
			status: 403,
			title: "You don't have permission to perform this operation",
			subtitle: 'Contact the administrator if you need access.',
		},
		{status: 500, title: "Couldn't create operation", subtitle: undefined},
	])(
		'should stay on the summary when the migration fails with $status',
		async ({status, title, subtitle}, {worker}) => {
			mockPage(worker);
			worker.use(mockCreateMigrationBatchOperationEndpoint({successResponse: new HttpResponse(null, {status})}));
			const screen = await renderPage();
			await enterSummary(screen);

			await confirmMigration(screen);

			await expect.element(screen.getByText(title)).toBeVisible();
			if (subtitle !== undefined) {
				await expect.element(screen.getByText(subtitle)).toBeVisible();
			}
			await expect.element(screen.getByText('Failed')).toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'Confirm'}), {timeout: 3000}).toBeVisible();
			await expect.element(screen.getByText('Failed')).not.toBeInTheDocument();
			await expect.element(screen.getByText('Migration step 2 - confirm')).toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'Back'})).toBeEnabled();
		},
	);

	it('should ask before navigating away from the summary', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterSummary(screen);

		screen.router.history.push('/operate/processes?process=my_simple_process');

		const dialog = screen.getByRole('dialog', {name: 'Leave Migration Mode'});
		await userEvent.click(dialog.getByRole('button', {name: 'Stay'}));

		await expect.element(dialog).not.toBeInTheDocument();
		await expect.element(screen.getByText('Migration step 2 - confirm')).toBeVisible();
		expect(screen.router.state.location.searchStr).toBe(`?${SEARCH}`);
	});

	describe('from a process instance', () => {
		const INSTANCE = createProcessInstance({
			processInstanceKey: '2251799813685249',
			processDefinitionKey: 'source-key',
			processDefinitionId: 'my_simple_process',
			processDefinitionName: 'Invoice process',
			processDefinitionVersion: 1,
			tenantId: 'tenant-a',
		});
		const INSTANCE_PATH = `/operate/processes/${INSTANCE.processInstanceKey}`;
		const SOURCE_SEARCH = {
			active: true,
			incidents: true,
			suspended: true,
			completed: false,
			canceled: false,
			process: 'my_simple_process',
			version: 1,
		};

		async function openFromInstance(state = getInstanceMigrationLocation(INSTANCE).state) {
			const screen = await renderWithRouter(
				() => (
					<>
						<ProcessesHarness />
						<Notifications />
					</>
				),
				{path: '/operate/processes', initialEntry: INSTANCE_PATH},
			);
			await screen.router.navigate({...getInstanceMigrationLocation(INSTANCE), state});
			return screen;
		}

		it.for([
			{multiTenancy: 'disabled', isMultiTenancyEnabled: false, tenantId: undefined},
			{multiTenancy: 'enabled', isMultiTenancyEnabled: true, tenantId: 'tenant-a'},
		])(
			'should migrate only the handed-over instance with multi-tenancy $multiTenancy',
			async ({isMultiTenancyEnabled, tenantId}, {worker}) => {
				sessionStorage.setItem(
					'clientConfig',
					JSON.stringify(
						createSystemConfiguration({
							deployment: {...createSystemConfiguration().deployment, isMultiTenancyEnabled},
						}),
					),
				);
				mockPage(worker);
				worker.use(
					mockCurrentUserEndpoint({
						successResponse: HttpResponse.json(
							createCurrentUser({tenants: [{tenantId: 'tenant-a', name: 'Tenant A', description: null}]}),
						),
					}),
					mockGetProcessDefinitionStatisticsEndpoint({
						schema: z.strictObject({
							filter: z.strictObject({
								$or: z.tuple([
									z.strictObject({state: z.strictObject({$eq: z.literal('ACTIVE')}), hasIncident: z.literal(false)}),
									z.strictObject({state: z.strictObject({$eq: z.literal('SUSPENDED')})}),
									z.strictObject({
										hasIncident: z.literal(true),
										state: z.strictObject({$neq: z.literal('SUSPENDED')}),
									}),
								]),
								...(isMultiTenancyEnabled ? {tenantId: z.strictObject({$eq: z.literal('tenant-a')})} : {}),
								processInstanceKey: z.strictObject({$in: z.tuple([z.literal(INSTANCE.processInstanceKey)])}),
							}),
						}),
						successResponse: HttpResponse.json(
							createGetProcessDefinitionStatisticsResponse([
								createProcessDefinitionStatistic({elementId: 'task-1', active: 1}),
							]),
						),
						failureResponse: new HttpResponse(null, {status: 400}),
					}),
					mockCreateMigrationBatchOperationEndpoint({
						schema: z.strictObject({
							filter: z.strictObject({
								$or: z.tuple([
									z.strictObject({state: z.strictObject({$eq: z.literal('ACTIVE')}), hasIncident: z.literal(false)}),
									z.strictObject({state: z.strictObject({$eq: z.literal('SUSPENDED')})}),
									z.strictObject({
										hasIncident: z.literal(true),
										state: z.strictObject({$neq: z.literal('SUSPENDED')}),
									}),
								]),
								state: z.strictObject({$eq: z.literal('ACTIVE')}),
								processDefinitionId: z.strictObject({$eq: z.literal('my_simple_process')}),
								processDefinitionVersion: z.literal(1),
								...(isMultiTenancyEnabled ? {tenantId: z.strictObject({$eq: z.literal('tenant-a')})} : {}),
								processInstanceKey: z.strictObject({$in: z.tuple([z.literal(INSTANCE.processInstanceKey)])}),
								processDefinitionKey: z.strictObject({$eq: z.literal('source-key')}),
							}),
							migrationPlan: z.strictObject({
								targetProcessDefinitionKey: z.literal('latest-key'),
								mappingInstructions: z.tuple([
									z.strictObject({sourceElementId: z.literal('task-1'), targetElementId: z.literal('task-1')}),
								]),
							}),
						}),
						successResponse: accepted(),
						failureResponse: new HttpResponse(null, {status: 400}),
					}),
				);

				const screen = await openFromInstance();

				await expect.element(screen.getByText('Migration step 1 - mapping elements')).toBeVisible();
				expect(screen.router.state.location.search).toEqual({...SOURCE_SEARCH, tenantId});
				await expect.poll(() => screen.router.state.location.state.operateInstanceMigration).toBeUndefined();

				await userEvent.click(screen.getByRole('button', {name: 'Next'}));

				await expect
					.element(screen.getByText(/^You are about to migrate/))
					.toHaveTextContent(
						'You are about to migrate 1 process instance from the process definition: Invoice process - version 1 to the process definition: Invoice process - version 3',
					);
				await expect.element(screen.getByTestId('state-overlay-task-1-active')).toHaveTextContent('1');

				await confirmMigration(screen);

				await expect
					.element(screen.getByText('The batch operation "Migrate Process Instance" has been started'))
					.toBeVisible();
				expect(screen.router.state.location.search).toEqual({
					active: true,
					suspended: true,
					incidents: true,
					process: 'my_simple_process',
					version: 3,
					tenantId,
				});
			},
		);

		it('should return to the source list after exiting and not re-enter the migration', async ({worker}) => {
			mockPage(worker);
			const screen = await openFromInstance();
			await expect.element(screen.getByText('Migration step 1 - mapping elements')).toBeVisible();

			await userEvent.click(screen.getByRole('button', {name: 'Exit migration'}));
			await userEvent.click(screen.getByRole('dialog', {name: 'Exit migration'}).getByRole('button', {name: 'Exit'}));

			await expect.element(screen.getByText('Migration step 1 - mapping elements')).not.toBeInTheDocument();
			await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('Invoice process');
			expect(screen.router.state.location.search).toEqual(SOURCE_SEARCH);

			screen.router.history.back();
			await expect.poll(() => screen.router.state.location.pathname).toBe(INSTANCE_PATH);
			screen.router.history.forward();

			await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('Invoice process');
			await expect.element(screen.getByText('Migration step 1 - mapping elements')).not.toBeInTheDocument();
		});

		it('should keep the hand-over in its tenant when another tenant shares the definition ID', async ({worker}) => {
			sessionStorage.setItem(
				'clientConfig',
				JSON.stringify(
					createSystemConfiguration({
						deployment: {...createSystemConfiguration().deployment, isMultiTenancyEnabled: true},
					}),
				),
			);
			const otherTenantVersion = createProcessDefinition({
				processDefinitionId: 'my_simple_process',
				processDefinitionKey: 'other-tenant-key',
				name: 'Invoice process',
				version: 4,
				tenantId: 'tenant-b',
			});
			mockPage(worker, {xmlByKey: {...XML_BY_KEY, 'other-tenant-key': BPMN_XML}});
			worker.use(
				mockCurrentUserEndpoint({
					successResponse: HttpResponse.json(
						createCurrentUser({
							tenants: [
								{tenantId: 'tenant-a', name: 'Tenant A', description: null},
								{tenantId: 'tenant-b', name: 'Tenant B', description: null},
							],
						}),
					),
				}),
				mockQueryProcessDefinitionsByFilterEndpoint({
					getResponse: (filter) =>
						HttpResponse.json(
							createQueryProcessDefinitionsResponse({
								items: (filter?.isLatestVersion
									? [FLIGHT_REGISTRATION]
									: [SOURCE, PREVIOUS_TARGET, LATEST_TARGET, otherTenantVersion]
								).filter(({tenantId}) => filter?.tenantId === undefined || tenantId === filter.tenantId),
							}),
						),
				}),
			);
			const screen = await openFromInstance();

			await expect.element(screen.getByRole('combobox', {name: 'Target Version'})).toHaveTextContent('3Open menu');
			await userEvent.click(screen.getByRole('button', {name: 'Next'}));
			await expect
				.element(screen.getByText(/^You are about to migrate/))
				.toHaveTextContent(
					'You are about to migrate 1 process instance from the process definition: Invoice process - version 1 to the process definition: Invoice process - version 3',
				);

			await userEvent.click(screen.getByRole('button', {name: 'Exit migration'}));
			await userEvent.click(screen.getByRole('dialog', {name: 'Exit migration'}).getByRole('button', {name: 'Exit'}));
			await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('Invoice process');
			screen.router.history.back();
			await expect.poll(() => screen.router.state.location.pathname).toBe(INSTANCE_PATH);
			screen.router.history.forward();

			await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('Invoice process');
			await expect.element(screen.getByText('Migration step 1 - mapping elements')).not.toBeInTheDocument();
			expect(screen.router.state.location.search).toEqual({...SOURCE_SEARCH, tenantId: 'tenant-a'});
		});

		it('should ask before returning to the instance', async ({worker}) => {
			mockPage(worker);
			const screen = await openFromInstance();
			await expect.element(screen.getByText('Migration step 1 - mapping elements')).toBeVisible();

			screen.router.history.push(INSTANCE_PATH);
			const dialog = screen.getByRole('dialog', {name: 'Leave Migration Mode'});
			await userEvent.click(dialog.getByRole('button', {name: 'Stay'}));

			await expect.element(screen.getByText('Migration step 1 - mapping elements')).toBeVisible();
			expect(screen.router.state.location.pathname).toBe('/operate/processes');

			screen.router.history.push(INSTANCE_PATH);
			await userEvent.click(dialog.getByRole('button', {name: 'Leave'}));

			await expect.poll(() => screen.router.state.location.pathname).toBe(INSTANCE_PATH);
		});

		it('should list the instances when the hand-over is malformed', async ({worker}) => {
			mockPage(worker);
			const screen = await openFromInstance(
				JSON.parse(`{"operateInstanceMigration": {"processInstanceKey": "${INSTANCE.processInstanceKey}"}}`),
			);

			await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('Invoice process');
			await expect.element(screen.getByText('Migration step 1 - mapping elements')).not.toBeInTheDocument();
			await expect.poll(() => screen.router.state.location.state.operateInstanceMigration).toBeUndefined();
		});
	});
});
