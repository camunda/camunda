/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, expect, vi, beforeEach, afterEach} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {FilterSidebar} from './FilterSidebar';

describe('<FilterSidebar />', () => {
	let previousPanelStates: string | null;

	beforeEach(() => {
		previousPanelStates = localStorage.getItem('operate.panelStates');
		localStorage.removeItem('operate.panelStates');
	});

	afterEach(() => {
		localStorage.removeItem('operate.panelStates');

		if (previousPanelStates !== null) {
			localStorage.setItem('operate.panelStates', previousPanelStates);
		}
	});

	it('should render children', async () => {
		const screen = await render(
			<FilterSidebar localStorageKey="isFiltersCollapsed" isResetButtonDisabled={false}>
				<div>my filters</div>
			</FilterSidebar>,
		);

		await expect.element(screen.getByText('my filters')).toBeInTheDocument();
	});

	it('should call onResetClick when the reset filters button is clicked', async () => {
		const onResetClick = vi.fn();
		const screen = await render(
			<FilterSidebar localStorageKey="isFiltersCollapsed" isResetButtonDisabled={false} onResetClick={onResetClick}>
				<div>my filters</div>
			</FilterSidebar>,
		);

		await userEvent.click(screen.getByRole('button', {name: 'Reset filters'}));

		expect(onResetClick).toHaveBeenCalledTimes(1);
	});

	it('should disable the reset filters button when isResetButtonDisabled is true', async () => {
		const onResetClick = vi.fn();
		const screen = await render(
			<FilterSidebar localStorageKey="isFiltersCollapsed" isResetButtonDisabled={true} onResetClick={onResetClick}>
				<div>my filters</div>
			</FilterSidebar>,
		);

		await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeDisabled();
	});

	it('should restore the collapsed state from local storage', async () => {
		storeStateLocally('operate.panelStates', {isFiltersCollapsed: true});

		const screen = await render(
			<FilterSidebar localStorageKey="isFiltersCollapsed" isResetButtonDisabled={false}>
				<div>my filters</div>
			</FilterSidebar>,
		);

		await expect.element(screen.getByTestId('collapsed-panel')).toBeInTheDocument();
	});

	it('should persist the collapsed state to local storage when toggled', async () => {
		const screen = await render(
			<FilterSidebar localStorageKey="isFiltersCollapsed" isResetButtonDisabled={false}>
				<div>my filters</div>
			</FilterSidebar>,
		);

		await expect.element(screen.getByTestId('expanded-panel')).toBeInTheDocument();
		expect(getStateLocally('operate.panelStates')?.isFiltersCollapsed).toBe(false);

		await userEvent.click(screen.getByRole('button', {name: /collapse/i}));

		await expect.element(screen.getByTestId('collapsed-panel')).toBeInTheDocument();
		expect(getStateLocally('operate.panelStates')?.isFiltersCollapsed).toBe(true);
	});

	it('should keep the filter icon visible in the expanded header', async () => {
		const screen = await render(
			<FilterSidebar localStorageKey="isFiltersCollapsed" isResetButtonDisabled={false}>
				<div>my filters</div>
			</FilterSidebar>,
		);

		await expect.element(screen.getByTestId('expanded-panel').getByRole('heading', {name: 'Filter'})).toBeVisible();
		expect(document.querySelector('[data-testid="expanded-panel"] header svg')).not.toBeNull();
	});

	it('should collapse to an icon-only rail that keeps the filters mounted but hidden', async () => {
		storeStateLocally('operate.panelStates', {isFiltersCollapsed: true});

		const screen = await render(
			<FilterSidebar localStorageKey="isFiltersCollapsed" isResetButtonDisabled={false}>
				<div>my filters</div>
			</FilterSidebar>,
		);

		await expect.element(screen.getByRole('button', {name: 'Expand Filter'})).toBeVisible();
		await expect.element(screen.getByText('my filters')).not.toBeVisible();
	});
});
