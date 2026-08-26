/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {Filters} from './Filters';

describe('<Filters />', () => {
	it('should render the current filter and sort selection', async () => {
		const screen = await renderWithRouter(() => <Filters filter="assigned-to-me" sortBy="due" />, {
			path: '/shadcn/tasklist',
		});

		await expect.element(screen.getByRole('combobox', {name: 'Filters'})).toHaveTextContent('Assigned to me');

		await userEvent.click(screen.getByRole('button', {name: 'Sort tasks'}));
		await expect.element(screen.getByRole('menuitemradio', {name: 'Due date', checked: true})).toBeVisible();
	});

	it('should show a placeholder when the active filter is not a built-in one', async () => {
		const screen = await renderWithRouter(() => <Filters filter="my-custom-filter" sortBy="creation" />, {
			path: '/shadcn/tasklist',
		});

		await expect.element(screen.getByRole('combobox', {name: 'Filters'})).toHaveTextContent('Custom');
	});

	it('should navigate to the selected built-in filter', async () => {
		const {router, ...screen} = await renderWithRouter(() => <Filters filter="all-open" sortBy="creation" />, {
			path: '/shadcn/tasklist',
		});

		await userEvent.click(screen.getByRole('combobox', {name: 'Filters'}));
		await userEvent.click(screen.getByRole('option', {name: 'Unassigned'}));

		await expect.poll(() => router.state.location.search).toEqual({filter: 'unassigned'});
	});

	it('should force completion sorting when switching to the completed filter', async () => {
		const {router, ...screen} = await renderWithRouter(() => <Filters filter="all-open" sortBy="due" />, {
			path: '/shadcn/tasklist',
		});

		await userEvent.click(screen.getByRole('combobox', {name: 'Filters'}));
		await userEvent.click(screen.getByRole('option', {name: 'Completed'}));

		await expect.poll(() => router.state.location.search).toEqual({filter: 'completed', sortBy: 'completion'});
	});

	it('should navigate to the selected sort option while preserving the filter', async () => {
		const {router, ...screen} = await renderWithRouter(() => <Filters filter="unassigned" sortBy="creation" />, {
			path: '/shadcn/tasklist',
			initialEntry: '/shadcn/tasklist?filter=unassigned&sortBy=creation',
		});

		await userEvent.click(screen.getByRole('button', {name: 'Sort tasks'}));
		await userEvent.click(screen.getByRole('menuitemradio', {name: 'Due date'}));

		await expect.poll(() => router.state.location.search).toEqual({filter: 'unassigned', sortBy: 'due'});
	});

	it('should only offer completion-date sorting for the completed filter', async () => {
		const screen = await renderWithRouter(() => <Filters filter="all-open" sortBy="creation" />, {
			path: '/shadcn/tasklist',
		});

		await userEvent.click(screen.getByRole('button', {name: 'Sort tasks'}));

		await expect.element(screen.getByRole('menuitemradio', {name: 'Completion date'})).not.toBeInTheDocument();
	});

	it('should offer completion-date sorting for the completed filter', async () => {
		const screen = await renderWithRouter(() => <Filters filter="completed" sortBy="completion" />, {
			path: '/shadcn/tasklist',
		});

		await userEvent.click(screen.getByRole('button', {name: 'Sort tasks'}));

		await expect.element(screen.getByRole('menuitemradio', {name: 'Completion date'})).toBeVisible();
	});
});
