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
import {Toaster, toast} from '@camunda/design-system';
import {useSearch} from '@tanstack/react-router';
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
import {Notifications} from '#/shared/notifications/shadcn.components/Notifications';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {processesSearchSchema} from '../processesFilter';
import {Processes} from './Processes';

const PATH = '/operate-preview/processes';
const HELPER_STORAGE_KEY = 'operate.hideMoveModificationHelperModal';
const DEFAULT_SEARCH = 'process=my_simple_process&version=1&tenantId=tenant-a&elementId=task-1';

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
		mockGetProcessDefinitionStatisticsEndpoint({
			successResponse: HttpResponse.json(
				createGetProcessDefinitionStatisticsResponse([
					createProcessDefinitionStatistic({elementId: 'task-1', active: 2, incidents: 1}),
				]),
			),
		}),
	);
}

function renderPage(search = DEFAULT_SEARCH) {
	return renderWithRouter(Harness, {path: PATH, initialEntry: `${PATH}?${search}`});
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
}

async function clickDiagramElement(elementId: string) {
	await expect.poll(() => document.querySelector(`[data-element-id="${elementId}"]`)).not.toBeNull();
	await userEvent.click(document.querySelector<SVGElement>(`[data-element-id="${elementId}"]`)!);
}

describe('<Processes /> batch modification (design system)', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(async () => {
		await cleanup();
		sessionStorage.clear();
		localStorage.clear();
		notificationsStore.reset();
		toast.dismiss();
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
	])('should disable Move when $condition', async ({search, instanceKey, reason}, {worker}) => {
		mockPage(worker);
		const screen = await renderPage(search);

		await selectInstance(screen, instanceKey);

		await expect.element(screen.getByRole('button', {name: 'Move'})).toBeDisabled();
		await expect.element(screen.getByText(reason, {exact: false})).toBeInTheDocument();
	});

	it('should enter the mode through the helper and remember to skip it', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await selectInstance(screen, '1');

		await userEvent.click(screen.getByRole('button', {name: 'Move'}));
		const helper = screen.getByRole('dialog', {name: 'Process instance batch move mode'});
		await expect.element(helper.getByRole('img', {name: 'A bpmn diagram with a selected element'})).toBeVisible();
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

	it('should lock the process filters and the bulk actions while in the mode', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();

		await enterMode(screen);

		await expect.element(screen.getByLabelText('Name')).toBeDisabled();
		await expect.element(screen.getByRole('button', {name: 'Move'})).toBeDisabled();
		await expect.element(screen.getByRole('button', {name: 'Cancel', exact: true})).toBeDisabled();
		await expect.element(screen.getByText('Not available in batch modification mode')).toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Review modification'})).toBeDisabled();
	});

	it('should select a target, show it in the notification, and undo it', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterMode(screen);

		await clickDiagramElement('end_event');

		await expect.element(screen.getByRole('button', {name: 'Review modification'})).toBeEnabled();
		await userEvent.click(screen.getByRole('button', {name: /^Undo move to/}));
		await expect.element(screen.getByRole('button', {name: 'Review modification'})).toBeDisabled();
	});

	it('should submit the move instructions after review', async ({worker}) => {
		mockPage(worker);
		worker.use(
			mockCreateModificationBatchOperationEndpoint({
				schema: z.looseObject({
					filter: z.looseObject({tenantId: z.strictObject({$eq: z.literal('tenant-a')})}),
					moveInstructions: z.tuple([
						z.strictObject({sourceElementId: z.literal('task-1'), targetElementId: z.literal('end_event')}),
					]),
				}),
				successResponse: HttpResponse.json(
					{batchOperationKey: 'batch-1', batchOperationType: 'MODIFY_PROCESS_INSTANCE'},
					{status: 202},
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(
					createBatchOperation({batchOperationKey: 'batch-1', batchOperationType: 'MODIFY_PROCESS_INSTANCE'}),
				),
			}),
		);
		const screen = await renderPage();
		await enterMode(screen);
		await clickDiagramElement('end_event');

		await userEvent.click(screen.getByRole('button', {name: 'Review modification'}));
		await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Apply'}));

		await expect
			.element(screen.getByText('The batch operation "Modify Process Instance" has been started'))
			.toBeVisible();
	});

	it('should leave the mode after confirming exit', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await enterMode(screen);

		await userEvent.click(screen.getByRole('button', {name: 'Exit'}));
		await userEvent.click(screen.getByRole('alertdialog').getByRole('button', {name: 'Exit'}));

		await expect.element(screen.getByText('Batch Modification Mode')).not.toBeInTheDocument();
		await expect.element(screen.getByLabelText('Name')).toBeEnabled();
	});
});
