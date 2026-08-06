/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createProcessDefinition} from '#/shared-test-modules/api-mocks/process-definitions';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {mockCurrentUserEndpoint} from '#/shared-test-modules/mock-handlers';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {HttpResponse} from 'msw';
import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {TasklistProcessesPage} from './TasklistProcessesPage';

describe('<TasklistProcessesPage />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should display all available processes', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));
		const screen = await renderWithRouter(
			() => (
				<TasklistProcessesPage
					initialFilterValues={{}}
					processes={[
						createProcessDefinition({name: 'Invoice review', processDefinitionKey: '1'}),
						createProcessDefinition({name: 'Order approval', processDefinitionKey: '2'}),
					]}
					hasNextPage={false}
					isFetchingNextPage={false}
					onLoadMore={vi.fn()}
					selectedProcessDefinitionKey={null}
					startProcessStatus="inactive"
					isStartProcessBusy={false}
					onStartProcess={vi.fn()}
				/>
			),
			{path: '/tasklist/processes'},
		);

		await expect.element(screen.getByRole('heading', {name: 'Invoice review'})).toBeVisible();
		await expect.element(screen.getByRole('heading', {name: 'Order approval'})).toBeVisible();
	});

	it('should display the unpublished-processes empty state', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));
		const screen = await renderWithRouter(
			() => (
				<TasklistProcessesPage
					initialFilterValues={{}}
					processes={[]}
					hasNextPage={false}
					isFetchingNextPage={false}
					onLoadMore={vi.fn()}
					selectedProcessDefinitionKey={null}
					startProcessStatus="inactive"
					isStartProcessBusy={false}
					onStartProcess={vi.fn()}
				/>
			),
			{path: '/tasklist/processes'},
		);

		await expect.element(screen.getByRole('heading', {name: 'No published processes yet'})).toBeVisible();
		await expect.element(screen.getByRole('img')).toBeVisible();
	});

	it('should display the no-matching-process empty state for a filtered list', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));
		const screen = await renderWithRouter(
			() => (
				<TasklistProcessesPage
					initialFilterValues={{search: 'missing'}}
					processes={[]}
					hasNextPage={false}
					isFetchingNextPage={false}
					onLoadMore={vi.fn()}
					selectedProcessDefinitionKey={null}
					startProcessStatus="inactive"
					isStartProcessBusy={false}
					onStartProcess={vi.fn()}
				/>
			),
			{path: '/tasklist/processes'},
		);

		await expect
			.element(screen.getByRole('heading', {name: 'We could not find any process with that name'}))
			.toBeVisible();
	});

	it('should link to the process publishing documentation', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));
		const screen = await renderWithRouter(
			() => (
				<TasklistProcessesPage
					initialFilterValues={{}}
					processes={[]}
					hasNextPage={false}
					isFetchingNextPage={false}
					onLoadMore={vi.fn()}
					selectedProcessDefinitionKey={null}
					startProcessStatus="inactive"
					isStartProcessBusy={false}
					onStartProcess={vi.fn()}
				/>
			),
			{path: '/tasklist/processes'},
		);

		await expect
			.element(screen.getByRole('link', {name: 'here'}))
			.toHaveAttribute(
				'href',
				'https://docs.camunda.io/docs/components/modeler/web-modeler/run-or-publish-your-process/#publishing-a-process',
			);
	});

	it('should display the load-more button when more processes are available', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));
		const screen = await renderWithRouter(
			() => (
				<TasklistProcessesPage
					initialFilterValues={{}}
					processes={[createProcessDefinition()]}
					hasNextPage
					isFetchingNextPage={false}
					onLoadMore={vi.fn()}
					selectedProcessDefinitionKey={null}
					startProcessStatus="inactive"
					isStartProcessBusy={false}
					onStartProcess={vi.fn()}
				/>
			),
			{path: '/tasklist/processes'},
		);

		await expect.element(screen.getByRole('button', {name: 'Load more'})).toBeVisible();
	});

	it('should call onStartProcess with the selected process', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));
		const process = createProcessDefinition({name: 'Invoice review'});
		const onStartProcess = vi.fn();
		const screen = await renderWithRouter(
			() => (
				<TasklistProcessesPage
					initialFilterValues={{}}
					processes={[process]}
					hasNextPage={false}
					isFetchingNextPage={false}
					onLoadMore={vi.fn()}
					selectedProcessDefinitionKey={null}
					startProcessStatus="inactive"
					isStartProcessBusy={false}
					onStartProcess={onStartProcess}
				/>
			),
			{path: '/tasklist/processes'},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Start process'}));

		expect(onStartProcess).toHaveBeenCalledWith(process);
	});

	it('should display the start status only for the selected process', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));
		const screen = await renderWithRouter(
			() => (
				<TasklistProcessesPage
					initialFilterValues={{}}
					processes={[
						createProcessDefinition({name: 'Invoice review', processDefinitionKey: '1'}),
						createProcessDefinition({name: 'Order approval', processDefinitionKey: '2'}),
					]}
					hasNextPage={false}
					isFetchingNextPage={false}
					onLoadMore={vi.fn()}
					selectedProcessDefinitionKey="1"
					startProcessStatus="active"
					isStartProcessBusy
					onStartProcess={vi.fn()}
				/>
			),
			{path: '/tasklist/processes'},
		);

		await expect.element(screen.getByText('Starting process...')).toHaveLength(1);
		await expect.element(screen.getByRole('button', {name: 'Start process'})).toHaveLength(1);
	});

	it('should disable all start-process buttons while a process start is busy', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));
		const screen = await renderWithRouter(
			() => (
				<TasklistProcessesPage
					initialFilterValues={{}}
					processes={[
						createProcessDefinition({name: 'Invoice review', processDefinitionKey: '1'}),
						createProcessDefinition({name: 'Order approval', processDefinitionKey: '2'}),
					]}
					hasNextPage={false}
					isFetchingNextPage={false}
					onLoadMore={vi.fn()}
					selectedProcessDefinitionKey={null}
					startProcessStatus="inactive"
					isStartProcessBusy
					onStartProcess={vi.fn()}
				/>
			),
			{path: '/tasklist/processes'},
		);

		const startButtons = screen.getByRole('button', {name: 'Start process'});
		await expect.element(startButtons).toHaveLength(2);
		await expect.element(startButtons.first()).toBeDisabled();
		await expect.element(startButtons.last()).toBeDisabled();
	});
});
