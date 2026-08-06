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
import {TasklistProcessesPage} from './TasklistProcessesPage';

const defaultProps = {
	initialFilterValues: {},
	processes: [],
	hasNextPage: false,
	isFetchingNextPage: false,
	onLoadMore: vi.fn(),
};

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
					{...defaultProps}
					processes={[
						createProcessDefinition({name: 'Invoice review', processDefinitionKey: '1'}),
						createProcessDefinition({name: 'Order approval', processDefinitionKey: '2'}),
					]}
				/>
			),
			{path: '/tasklist/processes'},
		);

		await expect.element(screen.getByRole('heading', {name: 'Invoice review'})).toBeVisible();
		await expect.element(screen.getByRole('heading', {name: 'Order approval'})).toBeVisible();
	});

	it('should display the unpublished-processes empty state', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));
		const screen = await renderWithRouter(() => <TasklistProcessesPage {...defaultProps} />, {
			path: '/tasklist/processes',
		});

		await expect.element(screen.getByRole('heading', {name: 'No published processes yet'})).toBeVisible();
		await expect.element(screen.getByRole('img')).toBeVisible();
	});

	it('should display the no-matching-process empty state for a filtered list', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));
		const screen = await renderWithRouter(
			() => <TasklistProcessesPage {...defaultProps} initialFilterValues={{search: 'missing'}} />,
			{path: '/tasklist/processes'},
		);

		await expect
			.element(screen.getByRole('heading', {name: 'We could not find any process with that name'}))
			.toBeVisible();
	});

	it('should link to the process publishing documentation', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));
		const screen = await renderWithRouter(() => <TasklistProcessesPage {...defaultProps} />, {
			path: '/tasklist/processes',
		});

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
			() => <TasklistProcessesPage {...defaultProps} processes={[createProcessDefinition()]} hasNextPage />,
			{path: '/tasklist/processes'},
		);

		await expect.element(screen.getByRole('button', {name: 'Load more'})).toBeVisible();
	});
});
