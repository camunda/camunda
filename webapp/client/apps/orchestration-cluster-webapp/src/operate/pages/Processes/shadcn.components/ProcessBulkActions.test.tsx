/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockCreateDeletionBatchOperationEndpoint,
	mockCreateResumptionBatchOperationEndpoint,
	mockGetBatchOperationEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockQueryProcessInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {createBatchOperation} from '#/shared-test-modules/api-mocks/batch-operations';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {Notifications} from '#/shared/notifications/components/Notifications';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import type {ProcessesSearch} from '../processesFilter';
import {InstancesTable} from './InstancesTable';

const SEARCH: ProcessesSearch = {
	active: true,
	incidents: true,
	completed: true,
	canceled: true,
	suspended: true,
	tenantId: 'tenant-a',
	process: 'orders',
	version: 2,
};
const ITEMS = [
	createProcessInstance({processInstanceKey: '1', state: 'ACTIVE', hasIncident: false}),
	createProcessInstance({processInstanceKey: '3', state: 'COMPLETED', hasIncident: false}),
	createProcessInstance({processInstanceKey: '5', state: 'SUSPENDED', hasIncident: false}),
];
const FAILURE_RESPONSE = new HttpResponse(null, {status: 400});

const accepted = (
	batchOperationType: 'CANCEL_PROCESS_INSTANCE' | 'RESUME_PROCESS_INSTANCE' | 'DELETE_PROCESS_INSTANCE',
) => HttpResponse.json({batchOperationKey: 'batch-op-1', batchOperationType}, {status: 202});

const requestFor = (keys: [string, ...string[]]) =>
	z.strictObject({
		filter: z.looseObject({
			processInstanceKey: z.strictObject({
				$in: z.tuple([z.literal(keys[0]), ...keys.slice(1).map((k) => z.literal(k))]),
			}),
		}),
	});

function renderTable() {
	return renderWithRouter(
		() => (
			<div style={{height: '100vh'}}>
				<InstancesTable search={SEARCH} />
				<Notifications />
			</div>
		),
		{path: '/operate-preview/processes', initialEntry: '/operate-preview/processes'},
	);
}

async function select(screen: Awaited<ReturnType<typeof renderTable>>, key: string) {
	await userEvent.click(screen.getByRole('checkbox', {name: `Select instance ${key}`, exact: true}), {force: true});
}

describe('Processes DS bulk actions', () => {
	it.beforeEach(({worker}) => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({items: ITEMS, page: {totalItems: 3, hasMoreTotalItems: false}}),
				),
			}),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json({items: [], page: {totalItems: 0}}),
			}),
		);
	});

	afterEach(() => {
		sessionStorage.clear();
		notificationsStore.reset();
	});

	it('should enable only the actions that apply to the selected instances', async () => {
		const screen = await renderTable();

		await select(screen, '1');

		await expect.element(screen.getByText('1 item selected', {exact: false})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Cancel', exact: true})).toBeEnabled();
		await expect.element(screen.getByRole('button', {name: 'Suspend', exact: true})).toBeEnabled();
		await expect.element(screen.getByRole('button', {name: 'Delete', exact: true})).toBeDisabled();
		await expect.element(screen.getByRole('button', {name: 'Resume', exact: true})).toBeDisabled();
		await expect.element(screen.getByRole('button', {name: 'Retry', exact: true})).toBeDisabled();
	});

	it('should explain why an action is unavailable for the selected instances', async () => {
		const screen = await renderTable();

		await select(screen, '1');

		await expect.element(screen.getByText('No finished process instances selected', {exact: false})).toBeVisible();
		await expect.element(screen.getByText('No suspended process instances selected', {exact: false})).toBeVisible();
	});

	it('should confirm and submit Resume for the selected suspended instance', async ({worker}) => {
		worker.use(
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createBatchOperation({batchOperationType: 'RESUME_PROCESS_INSTANCE'})),
			}),
			mockCreateResumptionBatchOperationEndpoint({
				schema: requestFor(['5']),
				successResponse: accepted('RESUME_PROCESS_INSTANCE'),
				failureResponse: FAILURE_RESPONSE,
			}),
		);
		const screen = await renderTable();
		await select(screen, '1');
		await select(screen, '5');

		await userEvent.click(screen.getByRole('button', {name: 'Resume', exact: true}));
		const dialog = screen.getByRole('alertdialog');
		await expect
			.element(dialog.getByText("Instances that aren't suspended in your selection will be ignored.", {exact: false}))
			.toBeVisible();
		await userEvent.click(dialog.getByRole('button', {name: 'Apply', exact: true}));

		await expect
			.element(screen.getByText('The batch operation "Resume Process Instance" has been started'))
			.toBeVisible();
		await expect.element(screen.getByRole('checkbox', {name: 'Select instance 5', exact: true})).not.toBeChecked();
	});

	it('should submit Delete through a destructive confirmation', async ({worker}) => {
		worker.use(
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createBatchOperation({batchOperationType: 'DELETE_PROCESS_INSTANCE'})),
			}),
			mockCreateDeletionBatchOperationEndpoint({
				schema: requestFor(['3']),
				successResponse: accepted('DELETE_PROCESS_INSTANCE'),
				failureResponse: FAILURE_RESPONSE,
			}),
		);
		const screen = await renderTable();
		await select(screen, '3');

		await userEvent.click(screen.getByRole('button', {name: 'Delete', exact: true}));
		const dialog = screen.getByRole('alertdialog');
		await expect
			.element(dialog.getByText('This permanently deletes the selected process instances', {exact: false}))
			.toBeVisible();
		await userEvent.click(dialog.getByRole('button', {name: 'Delete', exact: true}));

		await expect
			.element(screen.getByText('The batch operation "Delete Process Instance" has been started'))
			.toBeVisible();
	});

	it('should keep the selection when the dialog is dismissed and clear it on Cancel', async () => {
		const screen = await renderTable();
		await select(screen, '1');

		await userEvent.click(screen.getByRole('button', {name: 'Cancel', exact: true}));
		await userEvent.keyboard('{Escape}');
		await expect.element(screen.getByRole('checkbox', {name: 'Select instance 1', exact: true})).toBeChecked();

		await userEvent.click(screen.getByRole('button', {name: 'Cancel', exact: true}));
		await userEvent.click(screen.getByRole('alertdialog').getByRole('button', {name: 'Cancel', exact: true}));

		await expect.element(screen.getByRole('checkbox', {name: 'Select instance 1', exact: true})).not.toBeChecked();
	});
});
