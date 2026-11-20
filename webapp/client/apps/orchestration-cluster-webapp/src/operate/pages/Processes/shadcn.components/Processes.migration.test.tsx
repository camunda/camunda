/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Suspense} from 'react';
import {afterEach, beforeEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {useSearch} from '@tanstack/react-router';
import {http, HttpResponse} from 'msw';
import {endpoints} from '@camunda/camunda-api-zod-schemas/8.11';
import type {SetupWorker} from 'msw/browser';
import {Toaster} from '@camunda/design-system';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockCreateMigrationBatchOperationEndpoint,
	mockGetProcessDefinitionStatisticsEndpoint,
	mockGetProcessDefinitionXmlByKeyEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockQueryProcessDefinitionsByFilterEndpoint,
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
import {BPMN_XML, MIGRATION_SOURCE_BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {createQueryBatchOperationItemsResponse} from '#/shared-test-modules/api-mocks/batch-operations';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {Notifications} from '#/shared/notifications/shadcn.components/Notifications';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {getPreviewInstanceMigrationLocation} from '../instanceMigration';
import {processesSearchSchema} from '../processesFilter';
import {Processes} from './Processes';

const PATH = '/operate-preview/processes';
const SEARCH = 'process=my_simple_process&version=1&tenantId=tenant-a&active=true&completed=true';

const SOURCE = createProcessDefinition({
	processDefinitionId: 'my_simple_process',
	processDefinitionKey: 'source-key',
	name: 'Invoice process',
	version: 1,
	tenantId: 'tenant-a',
});
const TARGET = createProcessDefinition({
	processDefinitionId: 'my_simple_process',
	processDefinitionKey: 'latest-key',
	name: 'Invoice process',
	version: 2,
	tenantId: 'tenant-a',
});

const Harness = () => {
	const search = processesSearchSchema.parse(useSearch({strict: false}));
	return (
		<div style={{height: '100vh'}}>
			<Suspense fallback={null}>
				<Processes search={search} />
			</Suspense>
			<Notifications />
			<Toaster />
		</div>
	);
};

function mockPage(worker: SetupWorker) {
	worker.use(
		mockQueryProcessDefinitionsByFilterEndpoint({
			getResponse: () => HttpResponse.json(createQueryProcessDefinitionsResponse({items: [SOURCE, TARGET]})),
		}),
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [
						createProcessInstance({processInstanceKey: '1', state: 'ACTIVE'}),
						createProcessInstance({processInstanceKey: '3', state: 'COMPLETED'}),
					],
					page: {totalItems: 2, hasMoreTotalItems: false},
				}),
			),
		}),
		mockQueryBatchOperationItemsEndpoint({
			successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
		}),
		mockGetProcessDefinitionXmlByKeyEndpoint({
			xmlByKey: {'source-key': MIGRATION_SOURCE_BPMN_XML, 'latest-key': BPMN_XML},
		}),
		mockGetProcessDefinitionStatisticsEndpoint({
			successResponse: HttpResponse.json(
				createGetProcessDefinitionStatisticsResponse([
					createProcessDefinitionStatistic({elementId: 'task-1', active: 2}),
				]),
			),
		}),
	);
}

function renderPage() {
	return renderWithRouter(Harness, {path: PATH, initialEntry: `${PATH}?${SEARCH}`});
}

type Screen = Awaited<ReturnType<typeof renderPage>>;

async function enterMigration(screen: Screen) {
	await userEvent.click(screen.getByRole('checkbox', {name: 'Select instance 1'}), {force: true});
	await userEvent.click(screen.getByRole('button', {name: 'Migrate'}));
	await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Continue'}));
	await expect.element(screen.getByText('Migration step 1 - mapping elements')).toBeVisible();
}

async function confirmMigration(screen: Screen) {
	await userEvent.click(screen.getByRole('button', {name: 'Next'}));
	await expect.element(screen.getByText('Migration step 2 - confirm')).toBeVisible();
	await userEvent.click(screen.getByRole('button', {name: 'Confirm'}));
	const dialog = screen.getByRole('dialog', {name: 'Migration confirmation'});
	await userEvent.fill(dialog.getByRole('textbox', {name: 'Type MIGRATE to confirm'}), 'MIGRATE');
	await userEvent.click(dialog.getByRole('button', {name: 'Confirm'}));
}

describe('<Processes /> migration mode (design system)', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(async () => {
		await cleanup();
		sessionStorage.clear();
		localStorage.clear();
		notificationsStore.reset();
	});

	it('should enter migration mode for the selected instances', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();

		await enterMigration(screen);

		await expect.element(screen.getByRole('button', {name: 'Next'})).toBeEnabled();
		await expect.element(screen.getByRole('checkbox', {name: 'Select instance 1'})).not.toBeInTheDocument();
	});

	it('should disable Migrate when only finished instances are selected', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();

		await userEvent.click(screen.getByRole('checkbox', {name: 'Select instance 3'}), {force: true});

		await expect.element(screen.getByRole('button', {name: 'Migrate'})).toBeDisabled();
		await expect
			.element(screen.getByText('You can only migrate instances in active or incident state.', {exact: false}))
			.toBeInTheDocument();
	});

	it('should start the batch migration and show the target in the list', async ({worker}) => {
		mockPage(worker);
		const requestBodies: unknown[] = [];
		worker.use(
			http.post(endpoints.createMigrationBatchOperation.getUrl(), async ({request}) => {
				requestBodies.push(await request.json());
				return HttpResponse.json(
					{batchOperationKey: 'batch-1', batchOperationType: 'MIGRATE_PROCESS_INSTANCE'},
					{status: 202},
				);
			}),
		);
		const screen = await renderPage();
		await enterMigration(screen);

		await confirmMigration(screen);

		await expect
			.poll(() => requestBodies[0])
			.toMatchObject({
				filter: {processDefinitionKey: {$eq: 'source-key'}, processInstanceKey: {$in: ['1']}},
				migrationPlan: {
					targetProcessDefinitionKey: 'latest-key',
					mappingInstructions: [{sourceElementId: 'task-1', targetElementId: 'task-1'}],
				},
			});

		await expect
			.element(screen.getByText('The batch operation "Migrate Process Instance" has been started'))
			.toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Version'})).toHaveTextContent('2');
	});

	it.for([
		{status: 403, title: "You don't have permission to perform this operation"},
		{status: 500, title: "Couldn't create operation"},
	])('should stay on the summary when the migration fails with $status', async ({status, title}, {worker}) => {
		mockPage(worker);
		worker.use(mockCreateMigrationBatchOperationEndpoint({successResponse: new HttpResponse(null, {status})}));
		const screen = await renderPage();
		await enterMigration(screen);

		await confirmMigration(screen);

		await expect.element(screen.getByText(title)).toBeVisible();
		await expect.element(screen.getByText('Migration step 2 - confirm')).toBeVisible();
	});

	it('should show the instance counts on the source and target elements in the summary', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterMigration(screen);

		await userEvent.click(screen.getByRole('button', {name: 'Next'}));

		await expect.element(screen.getByRole('img', {name: /^Review invoice, .+: 2$/})).toBeVisible();
		await expect.element(screen.getByRole('img', {name: 'Review invoice, 2 added', exact: true})).toBeVisible();
	});

	it('should not highlight a mapping row when its target is changed', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterMigration(screen);
		const target = screen.getByRole('combobox', {name: 'Target element for Review invoice'});
		const elementButton = screen.getByRole('button', {name: 'Review invoice', exact: true});

		await userEvent.click(target);
		await userEvent.click(screen.getByRole('option', {name: 'Not mapped'}));

		await expect.element(target).toHaveTextContent('Not mapped');
		await expect.element(elementButton).toHaveAttribute('aria-pressed', 'false');
	});

	describe('leaving with a staged mapping', () => {
		async function stageMapping(screen: Screen) {
			await userEvent.click(screen.getByRole('combobox', {name: 'Target element for Review invoice'}));
			await userEvent.click(screen.getByRole('option', {name: 'Not mapped'}));
			await expect
				.element(screen.getByRole('combobox', {name: 'Target element for Review invoice'}))
				.toHaveTextContent('Not mapped');
		}

		it('should keep the staged mapping when staying after navigating away', async ({worker}) => {
			mockPage(worker);
			const screen = await renderPage();
			await enterMigration(screen);
			await stageMapping(screen);

			void screen.router.navigate({
				to: PATH,
				search: {process: 'my_simple_process', version: 1, tenantId: 'tenant-a'},
			});
			await userEvent.click(screen.getByRole('alertdialog').getByRole('button', {name: 'Stay'}));

			await expect.element(screen.getByText('Migration step 1 - mapping elements')).toBeVisible();
			await expect
				.element(screen.getByRole('combobox', {name: 'Target element for Review invoice'}))
				.toHaveTextContent('Not mapped');
		});

		it('should leave migration mode when leaving after navigating away', async ({worker}) => {
			mockPage(worker);
			const screen = await renderPage();
			await enterMigration(screen);
			await stageMapping(screen);

			void screen.router.navigate({
				to: PATH,
				search: {process: 'my_simple_process', version: 1, tenantId: 'tenant-a'},
			});
			await userEvent.click(screen.getByRole('alertdialog').getByRole('button', {name: 'Leave'}));

			await expect.element(screen.getByText('Migration step 1 - mapping elements')).not.toBeInTheDocument();
		});
	});

	it('should open migration mode for the instance handed over from the instance page', async ({worker}) => {
		mockPage(worker);
		const screen = await renderWithRouter(Harness, {path: PATH, initialEntry: `/operate-preview/processes/1`});
		const instance = createProcessInstance({
			processInstanceKey: '1',
			processDefinitionKey: 'source-key',
			processDefinitionId: 'my_simple_process',
			processDefinitionName: 'Invoice process',
			processDefinitionVersion: 1,
			tenantId: 'tenant-a',
		});

		await screen.router.navigate(getPreviewInstanceMigrationLocation(instance));

		await expect.element(screen.getByText('Migration step 1 - mapping elements')).toBeVisible();
	});
});
