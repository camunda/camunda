/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse, http} from 'msw';
import type {SetupWorker} from 'msw/browser';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {
	mockGetElementInstanceEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockQueryBatchOperationItemsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {endpoints} from '#/shared/http/endpoints';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {ProcessInstancePageProvider} from '../ProcessInstancePageProvider';
import {processInstanceSearchSchema} from '../processInstanceSearch';
import {useRouterState} from '@tanstack/react-router';
import {InstanceHistory} from './InstanceHistory';

const instance = createProcessInstance({processDefinitionName: 'Order process'});
const path = '/operate-preview/processes/$processInstanceId/variables';
const subprocess = createElementInstance({
	elementId: 'subprocess',
	elementInstanceKey: '2251799813800101',
	elementName: 'Subprocess',
	type: 'SUB_PROCESS',
	state: 'COMPLETED',
	endDate: '2026-01-15T11:00:00.000Z',
});
const leaf = createElementInstance({
	elementId: 'leaf',
	elementInstanceKey: '2251799813800102',
	elementName: 'Leaf task',
	state: 'ACTIVE',
});
const nested = createElementInstance({
	elementId: 'nested',
	elementInstanceKey: '2251799813800103',
	elementName: 'Nested task',
	hasIncident: true,
});

afterEach(async () => {
	await cleanup();
	notificationsStore.reset();
});

function Harness() {
	const search = useRouterState({select: (state) => state.location.search});
	return (
		<ProcessInstancePageProvider
			processInstanceId={instance.processInstanceKey}
			processInstance={instance}
			search={processInstanceSearchSchema.parse(search)}
		>
			<InstanceHistory />
		</ProcessInstancePageProvider>
	);
}

function renderHistory(worker: SetupWorker, query = '', rootStatus = 200) {
	worker.use(
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
		mockQueryBatchOperationItemsEndpoint({successResponse: HttpResponse.json(createQueryElementInstancesResponse())}),
		mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(leaf)}),
		http.post(endpoints.queryElementInstances({}).url, async ({request}) => {
			const {filter} = (await request.json()) as {filter: {elementInstanceScopeKey?: string}};
			if (filter.elementInstanceScopeKey === instance.processInstanceKey) {
				return rootStatus === 200
					? HttpResponse.json(createQueryElementInstancesResponse([subprocess, leaf]))
					: new HttpResponse(null, {status: rootStatus});
			}
			return HttpResponse.json(createQueryElementInstancesResponse([nested]));
		}),
	);
	return renderWithRouter(Harness, {
		path,
		initialEntry: `/operate-preview/processes/${instance.processInstanceKey}/variables${query}`,
	});
}

describe('InstanceHistory (design system)', () => {
	it('should render the process root with its top-level elements', async ({worker}) => {
		const screen = await renderHistory(worker);

		await expect.element(screen.getByRole('treeitem', {name: /Order process/})).toBeVisible();
		await expect.element(screen.getByRole('treeitem', {name: /Subprocess/})).toBeVisible();
		await expect.element(screen.getByRole('treeitem', {name: /Leaf task/})).toBeVisible();
	});

	it('should load and show the children of a sub process when it is expanded', async ({worker}) => {
		const screen = await renderHistory(worker);

		await userEvent.click(screen.getByRole('treeitem', {name: /Subprocess/}));
		await userEvent.keyboard('{ArrowRight}');

		await expect.element(screen.getByRole('treeitem', {name: /Nested task/})).toBeVisible();
	});

	it('should select the clicked element', async ({worker}) => {
		const screen = await renderHistory(worker);

		await userEvent.click(screen.getByRole('treeitem', {name: /Leaf task/}));

		await expect.element(screen.getByRole('treeitem', {name: /Leaf task/})).toHaveAttribute('aria-selected', 'true');
	});

	it('should show end dates only when the timestamps switch is on', async ({worker}) => {
		const screen = await renderHistory(worker);
		await expect.element(screen.getByRole('treeitem', {name: /Subprocess/})).toBeVisible();
		await expect.element(screen.getByText('2026-01-15', {exact: false})).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('switch', {name: 'End date'}));

		await expect.element(screen.getByText('2026-01-15', {exact: false})).toBeVisible();
	});

	it('should list only matching elements when a status filter is applied', async ({worker}) => {
		const screen = await renderHistory(worker, '?elementStatus=incidents');

		await expect.element(screen.getByText('1 matching element')).toBeVisible();
		await expect.element(screen.getByRole('treeitem', {name: /Nested task/})).toBeVisible();
	});

	it('should offer a retry when the history cannot be fetched', async ({worker}) => {
		const screen = await renderHistory(worker, '', 500);

		await expect.element(screen.getByText('Instance history could not be fetched. Please refresh.')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Try again'})).toBeVisible();
	});

	it('should switch to filtered results after typing in the search field', async ({worker}) => {
		const screen = await renderHistory(worker);
		await expect.element(screen.getByRole('treeitem', {name: /Leaf task/})).toBeVisible();

		await userEvent.fill(screen.getByRole('searchbox', {name: 'Search instance history'}), 'nested');

		await expect.element(screen.getByText('1 matching element'), {timeout: 5000}).toBeVisible();
		await expect.element(screen.getByRole('treeitem', {name: /Leaf task/})).not.toBeInTheDocument();
	});

	it('should filter by status through the status toggle', async ({worker}) => {
		const screen = await renderHistory(worker);

		await userEvent.click(screen.getByRole('radio', {name: 'Incidents'}));

		await expect.element(screen.getByText('1 matching element')).toBeVisible();
	});
});
