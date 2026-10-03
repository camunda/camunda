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
	it('should render nothing when all filters are visible', async () => {
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

		await expect.element(screen.getByRole('button')).not.toBeInTheDocument();
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

		await userEvent.click(screen.getByRole('button', {name: 'optional-filters-menu'}));

		await expect.element(screen.getByTestId('optional-filter-menuitem-b')).toHaveTextContent('Filter B');
		await expect.element(screen.getByTestId('optional-filter-menuitem-c')).toHaveTextContent('Filter C');
		await expect.element(screen.getByTestId('optional-filter-menuitem-a')).not.toBeInTheDocument();
	});

	it('should call onFilterSelect with the clicked filter id', async () => {
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

		await userEvent.click(screen.getByRole('button', {name: 'optional-filters-menu'}));
		await userEvent.click(screen.getByTestId('optional-filter-menuitem-b'));

		expect(onFilterSelect).toHaveBeenCalledWith('b');
	});
});
