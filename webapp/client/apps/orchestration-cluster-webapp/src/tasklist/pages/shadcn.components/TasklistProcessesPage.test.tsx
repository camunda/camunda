/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createProcessInstanceResponse} from '#/shared-test-modules/api-mocks/process-instances';
import {createProcessDefinition} from '#/shared-test-modules/api-mocks/process-definitions';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {mockCreateProcessInstanceEndpoint} from '#/shared-test-modules/mock-handlers';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {HttpResponse} from 'msw';
import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {StartProcessProvider} from '#/tasklist/modules/processes/StartProcessProvider';
import {storeStateLocally} from '#/shared/browser-storage/local-storage';
import {TasklistProcessesPage} from './TasklistProcessesPage';

describe('<TasklistProcessesPage />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		storeStateLocally('tasklist.hasConsentedToStartProcess', true);
	});

	afterEach(() => {
		sessionStorage.clear();
		localStorage.clear();
	});

	it('should display all available processes', async () => {
		const screen = await renderWithRouter(
			() => (
				<StartProcessProvider>
					<TasklistProcessesPage
						initialFilterValues={{}}
						tenants={[]}
						processes={[
							createProcessDefinition({name: 'Invoice review', processDefinitionKey: '1'}),
							createProcessDefinition({name: 'Order approval', processDefinitionKey: '2'}),
						]}
						hasNextPage={false}
						isFetchingNextPage={false}
						onLoadMore={vi.fn()}
						onOpenStartProcessForm={vi.fn()}
					/>
				</StartProcessProvider>
			),
			{path: '/shadcn/tasklist/processes'},
		);

		await expect.element(screen.getByRole('heading', {name: 'Invoice review'})).toBeVisible();
		await expect.element(screen.getByRole('heading', {name: 'Order approval'})).toBeVisible();
	});

	it('should display the unpublished-processes empty state', async () => {
		const screen = await renderWithRouter(
			() => (
				<StartProcessProvider>
					<TasklistProcessesPage
						initialFilterValues={{}}
						tenants={[]}
						processes={[]}
						hasNextPage={false}
						isFetchingNextPage={false}
						onLoadMore={vi.fn()}
						onOpenStartProcessForm={vi.fn()}
					/>
				</StartProcessProvider>
			),
			{path: '/shadcn/tasklist/processes'},
		);

		await expect.element(screen.getByRole('heading', {name: 'No published processes yet'})).toBeVisible();
	});

	it('should display the no-matching-process empty state for a filtered list', async () => {
		const screen = await renderWithRouter(
			() => (
				<StartProcessProvider>
					<TasklistProcessesPage
						initialFilterValues={{search: 'missing'}}
						tenants={[]}
						processes={[]}
						hasNextPage={false}
						isFetchingNextPage={false}
						onLoadMore={vi.fn()}
						onOpenStartProcessForm={vi.fn()}
					/>
				</StartProcessProvider>
			),
			{path: '/shadcn/tasklist/processes'},
		);

		await expect
			.element(screen.getByRole('heading', {name: 'We could not find any process with that name'}))
			.toBeVisible();
	});

	it('should link to the process publishing documentation', async () => {
		const screen = await renderWithRouter(
			() => (
				<StartProcessProvider>
					<TasklistProcessesPage
						initialFilterValues={{}}
						tenants={[]}
						processes={[]}
						hasNextPage={false}
						isFetchingNextPage={false}
						onLoadMore={vi.fn()}
						onOpenStartProcessForm={vi.fn()}
					/>
				</StartProcessProvider>
			),
			{path: '/shadcn/tasklist/processes'},
		);

		await expect
			.element(screen.getByRole('link', {name: 'here'}))
			.toHaveAttribute(
				'href',
				'https://docs.camunda.io/docs/components/modeler/web-modeler/run-or-publish-your-process/#publishing-a-process',
			);
	});

	it('should display the load-more button when more processes are available', async () => {
		const screen = await renderWithRouter(
			() => (
				<StartProcessProvider>
					<TasklistProcessesPage
						initialFilterValues={{}}
						tenants={[]}
						processes={[createProcessDefinition()]}
						hasNextPage
						isFetchingNextPage={false}
						onLoadMore={vi.fn()}
						onOpenStartProcessForm={vi.fn()}
					/>
				</StartProcessProvider>
			),
			{path: '/shadcn/tasklist/processes'},
		);

		await expect.element(screen.getByRole('button', {name: 'Load more'})).toBeVisible();
	});

	it('should open the start form for the selected process', async () => {
		const process = createProcessDefinition({name: 'Invoice review', hasStartForm: true});
		const onOpenStartProcessForm = vi.fn();
		const screen = await renderWithRouter(
			() => (
				<StartProcessProvider>
					<TasklistProcessesPage
						initialFilterValues={{}}
						tenants={[]}
						processes={[process]}
						hasNextPage={false}
						isFetchingNextPage={false}
						onLoadMore={vi.fn()}
						onOpenStartProcessForm={onOpenStartProcessForm}
					/>
				</StartProcessProvider>
			),
			{path: '/shadcn/tasklist/processes'},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Start process'}));

		expect(onOpenStartProcessForm).toHaveBeenCalledWith(process.processDefinitionKey);
	});

	it('should display the start status only for the selected process', async ({worker}) => {
		worker.use(
			mockCreateProcessInstanceEndpoint({
				delay: 5000,
				successResponse: HttpResponse.json(createProcessInstanceResponse()),
			}),
		);
		const screen = await renderWithRouter(
			() => (
				<StartProcessProvider>
					<TasklistProcessesPage
						initialFilterValues={{}}
						tenants={[]}
						processes={[
							createProcessDefinition({name: 'Invoice review', processDefinitionKey: '1'}),
							createProcessDefinition({name: 'Order approval', processDefinitionKey: '2'}),
						]}
						hasNextPage={false}
						isFetchingNextPage={false}
						onLoadMore={vi.fn()}
						onOpenStartProcessForm={vi.fn()}
					/>
				</StartProcessProvider>
			),
			{path: '/shadcn/tasklist/processes'},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Start process'}).first());

		await expect.element(screen.getByText('Starting process...')).toHaveLength(1);
		await expect.element(screen.getByRole('button', {name: 'Start process'})).toHaveLength(1);
	});

	it('should disable all start-process buttons while a process start is busy', async ({worker}) => {
		worker.use(
			mockCreateProcessInstanceEndpoint({
				delay: 5000,
				successResponse: HttpResponse.json(createProcessInstanceResponse()),
			}),
		);
		const screen = await renderWithRouter(
			() => (
				<StartProcessProvider>
					<TasklistProcessesPage
						initialFilterValues={{}}
						tenants={[]}
						processes={[
							createProcessDefinition({name: 'Invoice review', processDefinitionKey: '1'}),
							createProcessDefinition({name: 'Order approval', processDefinitionKey: '2'}),
						]}
						hasNextPage={false}
						isFetchingNextPage={false}
						onLoadMore={vi.fn()}
						onOpenStartProcessForm={vi.fn()}
					/>
				</StartProcessProvider>
			),
			{path: '/shadcn/tasklist/processes'},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Start process'}).first());

		const startButtons = screen.getByRole('button', {name: 'Start process'});
		await expect.element(startButtons).toHaveLength(1);
		await expect.element(startButtons).toBeDisabled();
	});
});
