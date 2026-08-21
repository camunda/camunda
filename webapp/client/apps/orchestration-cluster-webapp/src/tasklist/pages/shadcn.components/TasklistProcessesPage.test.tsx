/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createProcessDefinition} from '#/shared-test-modules/api-mocks/process-definitions';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
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

	it('should display the page heading and description', async () => {
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

		await expect.element(screen.getByRole('heading', {name: 'Processes', exact: true})).toBeVisible();
		await expect.element(screen.getByText('Browse and run processes published by your organization.')).toBeVisible();
	});

	it('should display the filter bar', async () => {
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

		await expect.element(screen.getByRole('searchbox', {name: 'Search processes'})).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Filter processes'})).toBeVisible();
	});

	it('should display a tile per process with an enabled start-process action', async () => {
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
		const startProcessButtons = screen.getByRole('button', {name: 'Start process'});
		expect(startProcessButtons.elements()).toHaveLength(2);
		await expect.element(startProcessButtons.first()).toBeEnabled();
	});

	it('should open the start-process form for a process that has a start form', async () => {
		const onOpenStartProcessForm = vi.fn();
		const screen = await renderWithRouter(
			() => (
				<StartProcessProvider>
					<TasklistProcessesPage
						initialFilterValues={{}}
						tenants={[]}
						processes={[
							createProcessDefinition({name: 'Invoice review', processDefinitionKey: '1', hasStartForm: true}),
						]}
						hasNextPage={false}
						isFetchingNextPage={false}
						onLoadMore={vi.fn()}
						onOpenStartProcessForm={onOpenStartProcessForm}
					/>
				</StartProcessProvider>
			),
			{path: '/shadcn/tasklist/processes'},
		);

		await screen.getByRole('button', {name: 'Start process'}).click();

		expect(onOpenStartProcessForm).toHaveBeenCalledWith('1');
	});

	it('should display an empty message when there are no processes', async () => {
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

		await expect.element(screen.getByText('No published processes yet')).toBeVisible();
	});

	it('should display a not-found message when filtered and no processes match', async () => {
		const screen = await renderWithRouter(
			() => (
				<StartProcessProvider>
					<TasklistProcessesPage
						initialFilterValues={{search: 'invoice'}}
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

		await expect.element(screen.getByText('We could not find any process with that name')).toBeVisible();
	});

	it('should display a load-more button when there is a next page', async () => {
		const onLoadMore = vi.fn();
		const screen = await renderWithRouter(
			() => (
				<StartProcessProvider>
					<TasklistProcessesPage
						initialFilterValues={{}}
						tenants={[]}
						processes={[createProcessDefinition({name: 'Invoice review'})]}
						hasNextPage
						isFetchingNextPage={false}
						onLoadMore={onLoadMore}
						onOpenStartProcessForm={vi.fn()}
					/>
				</StartProcessProvider>
			),
			{path: '/shadcn/tasklist/processes'},
		);

		await screen.getByRole('button', {name: 'Load more'}).click();

		expect(onLoadMore).toHaveBeenCalledOnce();
	});
});
