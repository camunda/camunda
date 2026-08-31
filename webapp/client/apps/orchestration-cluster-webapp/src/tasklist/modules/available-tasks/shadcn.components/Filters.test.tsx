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
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockCurrentUserEndpoint} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {Filters} from './Filters';

const CURRENT_USER_MOCK = mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())});

describe('<Filters />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should render filters', async ({worker}) => {
		worker.use(CURRENT_USER_MOCK);
		const screen = await renderWithRouter(() => <Filters />, {
			path: '/shadcn/_auth/tasklist/_tasks',
			initialEntry: '/shadcn/_auth/tasklist/_tasks?filter=all-open&sortBy=creation',
		});

		await expect.element(screen.getByRole('button', {name: 'Filters'})).toHaveTextContent('All open tasks');

		await userEvent.click(screen.getByRole('button', {name: 'Sort tasks'}));

		await expect.element(screen.getByRole('menuitemradio', {name: 'Creation date'})).toBeVisible();
		await expect.element(screen.getByRole('menuitemradio', {name: 'Follow-up date'})).toBeVisible();
		await expect.element(screen.getByRole('menuitemradio', {name: 'Due date'})).toBeVisible();
		await expect.element(screen.getByRole('menuitemradio', {name: 'Completion date'})).not.toBeInTheDocument();
	});

	it('should enable sorting by completion date', async ({worker}) => {
		worker.use(CURRENT_USER_MOCK);
		const screen = await renderWithRouter(() => <Filters />, {
			path: '/shadcn/_auth/tasklist/_tasks',
			initialEntry: '/shadcn/_auth/tasklist/_tasks?filter=completed&sortBy=completion',
		});

		await userEvent.click(screen.getByRole('button', {name: 'Sort tasks'}));

		await expect.element(screen.getByRole('menuitemradio', {name: 'Completion date'})).toBeVisible();
	});

	it('should persist sorting in the URL', async ({worker}) => {
		worker.use(CURRENT_USER_MOCK);
		const {router, ...screen} = await renderWithRouter(() => <Filters />, {
			path: '/shadcn/_auth/tasklist/_tasks',
			initialEntry: '/shadcn/_auth/tasklist/_tasks?filter=assigned-to-me&sortBy=creation',
		});

		await userEvent.click(screen.getByRole('button', {name: 'Sort tasks'}));
		await userEvent.click(screen.getByRole('menuitemradio', {name: 'Due date'}));

		await expect.poll(() => router.state.location.search).toEqual({filter: 'assigned-to-me', sortBy: 'due'});
	});

	it('should disable sorting controls', async ({worker}) => {
		worker.use(CURRENT_USER_MOCK);
		const screen = await renderWithRouter(() => <Filters disabled />, {
			path: '/shadcn/_auth/tasklist/_tasks',
			initialEntry: '/shadcn/_auth/tasklist/_tasks?filter=all-open&sortBy=creation',
		});

		await expect.element(screen.getByRole('button', {name: 'Sort tasks'})).toBeDisabled();
	});

	it('should render the correct filter label', async ({worker}) => {
		worker.use(CURRENT_USER_MOCK);
		const allOpenScreen = await renderWithRouter(() => <Filters />, {
			path: '/shadcn/_auth/tasklist/_tasks',
			initialEntry: '/shadcn/_auth/tasklist/_tasks?filter=all-open&sortBy=creation',
		});

		await expect.element(allOpenScreen.getByRole('button', {name: 'Filters'})).toHaveTextContent('All open tasks');
		await cleanup();

		const assignedScreen = await renderWithRouter(() => <Filters />, {
			path: '/shadcn/_auth/tasklist/_tasks',
			initialEntry: '/shadcn/_auth/tasklist/_tasks?filter=assigned-to-me&sortBy=creation',
		});

		await expect.element(assignedScreen.getByRole('button', {name: 'Filters'})).toHaveTextContent('Assigned to me');
		await cleanup();

		const unassignedScreen = await renderWithRouter(() => <Filters />, {
			path: '/shadcn/_auth/tasklist/_tasks',
			initialEntry: '/shadcn/_auth/tasklist/_tasks?filter=unassigned&sortBy=creation',
		});

		await expect.element(unassignedScreen.getByRole('button', {name: 'Filters'})).toHaveTextContent('Unassigned');
		await cleanup();

		const completedScreen = await renderWithRouter(() => <Filters />, {
			path: '/shadcn/_auth/tasklist/_tasks',
			initialEntry: '/shadcn/_auth/tasklist/_tasks?filter=completed&sortBy=completion',
		});

		await expect.element(completedScreen.getByRole('button', {name: 'Filters'})).toHaveTextContent('Completed');
		await cleanup();

		const customScreen = await renderWithRouter(() => <Filters />, {
			path: '/shadcn/_auth/tasklist/_tasks',
			initialEntry: '/shadcn/_auth/tasklist/_tasks?filter=custom&sortBy=creation',
		});

		await expect.element(customScreen.getByRole('button', {name: 'Filters'})).toHaveTextContent('Custom');
	});
});
