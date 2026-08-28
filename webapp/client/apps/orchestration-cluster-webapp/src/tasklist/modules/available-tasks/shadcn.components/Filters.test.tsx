/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {Filters} from './Filters';

describe('<Filters />', () => {
	it('should render filters', async () => {
		const screen = await renderWithRouter(() => <Filters filter="all-open" sortBy="creation" />, {
			path: '/shadcn/tasklist',
		});

		await expect.element(screen.getByRole('combobox', {name: 'Filters'})).toHaveTextContent('All open tasks');

		await userEvent.click(screen.getByRole('button', {name: 'Sort tasks'}));

		await expect.element(screen.getByRole('menuitemradio', {name: 'Creation date'})).toBeVisible();
		await expect.element(screen.getByRole('menuitemradio', {name: 'Follow-up date'})).toBeVisible();
		await expect.element(screen.getByRole('menuitemradio', {name: 'Due date'})).toBeVisible();
		await expect.element(screen.getByRole('menuitemradio', {name: 'Completion date'})).not.toBeInTheDocument();
	});

	it('should enable sorting by completion date', async () => {
		const screen = await renderWithRouter(() => <Filters filter="completed" sortBy="completion" />, {
			path: '/shadcn/tasklist',
		});

		await userEvent.click(screen.getByRole('button', {name: 'Sort tasks'}));

		await expect.element(screen.getByRole('menuitemradio', {name: 'Completion date'})).toBeVisible();
	});

	it('should persist sorting in the URL', async () => {
		const {router, ...screen} = await renderWithRouter(() => <Filters filter="assigned-to-me" sortBy="creation" />, {
			path: '/shadcn/tasklist',
			initialEntry: '/shadcn/tasklist?filter=assigned-to-me',
		});

		await userEvent.click(screen.getByRole('button', {name: 'Sort tasks'}));
		await userEvent.click(screen.getByRole('menuitemradio', {name: 'Due date'}));

		await expect.poll(() => router.state.location.search).toEqual({filter: 'assigned-to-me', sortBy: 'due'});
	});

	it('should disable sorting controls', async () => {
		const screen = await renderWithRouter(() => <Filters filter="all-open" sortBy="creation" disabled />, {
			path: '/shadcn/tasklist',
		});

		await expect.element(screen.getByRole('button', {name: 'Sort tasks'})).toBeDisabled();
	});

	it('should render the correct filter label', async () => {
		const allOpenScreen = await renderWithRouter(() => <Filters filter="all-open" sortBy="creation" />, {
			path: '/shadcn/tasklist',
		});

		await expect.element(allOpenScreen.getByRole('combobox', {name: 'Filters'})).toHaveTextContent('All open tasks');
		await cleanup();

		const assignedScreen = await renderWithRouter(() => <Filters filter="assigned-to-me" sortBy="creation" />, {
			path: '/shadcn/tasklist',
		});

		await expect.element(assignedScreen.getByRole('combobox', {name: 'Filters'})).toHaveTextContent('Assigned to me');
		await cleanup();

		const unassignedScreen = await renderWithRouter(() => <Filters filter="unassigned" sortBy="creation" />, {
			path: '/shadcn/tasklist',
		});

		await expect.element(unassignedScreen.getByRole('combobox', {name: 'Filters'})).toHaveTextContent('Unassigned');
		await cleanup();

		const completedScreen = await renderWithRouter(() => <Filters filter="completed" sortBy="completion" />, {
			path: '/shadcn/tasklist',
		});

		await expect.element(completedScreen.getByRole('combobox', {name: 'Filters'})).toHaveTextContent('Completed');
		await cleanup();

		await renderWithRouter(() => <Filters filter="custom" sortBy="creation" />, {
			path: '/shadcn/tasklist',
		});
	});
});
