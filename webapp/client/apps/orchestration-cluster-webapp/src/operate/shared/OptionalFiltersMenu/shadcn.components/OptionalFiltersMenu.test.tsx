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
	it('should disable the combobox with a tooltip when all filters are visible', async () => {
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

		await expect.element(screen.getByRole('combobox')).toBeDisabled();
		await userEvent.hover(screen.getByRole('combobox'), {force: true});
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

		await userEvent.click(screen.getByRole('combobox', {name: 'More filters'}));

		await expect.element(screen.getByRole('option', {name: 'Filter B'})).toHaveTextContent('Filter B');
		await expect.element(screen.getByRole('option', {name: 'Filter C'})).toHaveTextContent('Filter C');
		await expect.element(screen.getByRole('option', {name: 'Filter A'})).not.toBeInTheDocument();
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

		await userEvent.click(screen.getByRole('combobox', {name: 'More filters'}));
		await userEvent.click(screen.getByRole('option', {name: 'Filter B'}));

		expect(onFilterSelect).toHaveBeenCalledWith('b');
	});
});
