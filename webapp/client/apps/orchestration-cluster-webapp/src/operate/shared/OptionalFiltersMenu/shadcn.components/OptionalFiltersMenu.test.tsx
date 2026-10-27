/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {OptionalFiltersMenu} from './OptionalFiltersMenu';

describe('<OptionalFiltersMenu />', () => {
	it('should disable the button with a tooltip when all filters are visible', async () => {
		const screen = await render(
			<OptionalFiltersMenu
				visibleFilters={['a', 'b']}
				optionalFilters={[
					{id: 'a', label: 'Filter A'},
					{id: 'b', label: 'Filter B'},
				]}
				onFilterSelect={vi.fn()}
			/>,
		);

		await expect.element(screen.getByRole('button', {name: 'More filters'})).toHaveAttribute('aria-disabled', 'true');
		screen.getByRole('button', {name: 'More filters'}).element().focus();
		await expect.element(screen.getByRole('tooltip')).toHaveTextContent('All filters are already added');
	});

	it('should list only the unselected optional filters', async () => {
		const screen = await render(
			<OptionalFiltersMenu
				visibleFilters={['a']}
				optionalFilters={[
					{id: 'a', label: 'Filter A'},
					{id: 'b', label: 'Filter B'},
					{id: 'c', label: 'Filter C'},
				]}
				onFilterSelect={vi.fn()}
			/>,
		);

		await userEvent.click(screen.getByRole('button', {name: 'More filters'}));

		await expect.element(screen.getByRole('menuitem', {name: 'Filter B'})).toHaveTextContent('Filter B');
		await expect.element(screen.getByRole('menuitem', {name: 'Filter C'})).toHaveTextContent('Filter C');
		await expect.element(screen.getByRole('menuitem', {name: 'Filter A'})).not.toBeInTheDocument();
	});

	it('should call onFilterSelect with the selected filter id', async () => {
		const onFilterSelect = vi.fn();
		const screen = await render(
			<OptionalFiltersMenu
				visibleFilters={['a']}
				optionalFilters={[
					{id: 'a', label: 'Filter A'},
					{id: 'b', label: 'Filter B'},
				]}
				onFilterSelect={onFilterSelect}
			/>,
		);

		await userEvent.click(screen.getByRole('button', {name: 'More filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Filter B'}));

		expect(onFilterSelect).toHaveBeenCalledWith('b');
	});

	it('should return focus to the trigger when the menu is dismissed without a selection', async () => {
		const screen = await render(
			<OptionalFiltersMenu
				visibleFilters={[]}
				optionalFilters={[{id: 'a', label: 'Filter A'}]}
				onFilterSelect={vi.fn()}
			/>,
		);
		const trigger = screen.getByRole('button', {name: 'More filters'});

		await userEvent.click(trigger);
		await userEvent.keyboard('{Escape}');

		await expect.element(trigger).toHaveFocus();
	});

	it('should focus the filter field after selecting a filter', async () => {
		const screen = await render(
			<>
				<OptionalFiltersMenu
					visibleFilters={[]}
					optionalFilters={[{id: 'a', label: 'Filter A'}]}
					onFilterSelect={vi.fn()}
				/>
				<input id="a" aria-label="Field A" />
			</>,
		);

		await userEvent.click(screen.getByRole('button', {name: 'More filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Filter A'}));

		await expect.element(screen.getByRole('textbox', {name: 'Field A'})).toHaveFocus();
	});

	it('should return focus to the trigger when the selected filter has no field to focus', async () => {
		const screen = await render(
			<OptionalFiltersMenu
				visibleFilters={[]}
				optionalFilters={[{id: 'a', label: 'Filter A'}]}
				onFilterSelect={vi.fn()}
			/>,
		);
		const trigger = screen.getByRole('button', {name: 'More filters'});

		await userEvent.click(trigger);
		await userEvent.click(screen.getByRole('menuitem', {name: 'Filter A'}));

		await expect.element(trigger).toHaveFocus();
	});
});
