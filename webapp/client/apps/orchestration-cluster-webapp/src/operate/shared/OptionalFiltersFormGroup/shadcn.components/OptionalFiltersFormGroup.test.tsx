/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Form} from 'react-final-form';
import {render} from 'vitest-browser-react';
import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {OptionalFiltersFormGroup, type OptionalFilterDefinition} from './OptionalFiltersFormGroup';

type FilterId = 'hasRetriesLeft' | 'errorMessage' | 'startDateRange' | 'endDateRange' | 'custom';

const getDefinitions = (onRemoveCustom: () => void): OptionalFilterDefinition<FilterId>[] => [
	{id: 'hasRetriesLeft', keys: ['hasRetriesLeft'], label: 'Has retries left', type: 'checkbox'},
	{id: 'errorMessage', keys: ['errorMessage'], label: 'Error message', type: 'text'},
	{
		id: 'startDateRange',
		keys: ['startDateFrom', 'startDateTo'],
		label: 'Start date range',
		type: 'dateRange',
		popoverTitle: 'Filter by start date',
		fromKey: 'startDateFrom',
		toKey: 'startDateTo',
	},
	{
		id: 'endDateRange',
		keys: ['endDateFrom', 'endDateTo'],
		label: 'End date range',
		type: 'dateRange',
		popoverTitle: 'Filter by end date',
		fromKey: 'endDateFrom',
		toKey: 'endDateTo',
	},
	{
		id: 'custom',
		keys: [],
		label: 'Custom filter',
		type: 'custom',
		render: () => <div>custom content</div>,
		onRemove: onRemoveCustom,
	},
];

const Harness: React.FC<{
	initialVisible?: FilterId[];
	activeFilters?: FilterId[];
	onSubmit?: (values: Record<string, unknown>) => void;
	onRemoveCustom?: () => void;
}> = ({initialVisible = [], activeFilters = [], onSubmit = vi.fn(), onRemoveCustom = vi.fn()}) => {
	const [visibleFilters, setVisibleFilters] = useState<FilterId[]>(initialVisible);

	return (
		<Form onSubmit={onSubmit}>
			{({handleSubmit}) => (
				<form onSubmit={handleSubmit}>
					<OptionalFiltersFormGroup
						definitions={getDefinitions(onRemoveCustom)}
						activeFilters={activeFilters}
						visibleFilters={visibleFilters}
						onVisibleFilterChange={setVisibleFilters}
						getRemoveFilterLabel={(label) => `Remove ${label} Filter`}
					/>
					<button type="submit">submit</button>
				</form>
			)}
		</Form>
	);
};

describe('<OptionalFiltersFormGroup /> (shared, design system)', () => {
	it('should show filters that are reported as active', async () => {
		const screen = await render(<Harness activeFilters={['errorMessage', 'custom']} />);

		await expect.element(screen.getByLabelText('Error message')).toBeVisible();
		await expect.element(screen.getByText('custom content')).toBeVisible();
	});

	it('should serialize a checkbox filter', async () => {
		const onSubmit = vi.fn();
		const screen = await render(<Harness initialVisible={['hasRetriesLeft']} onSubmit={onSubmit} />);

		await userEvent.click(screen.getByRole('checkbox', {name: 'Has retries left'}));
		await userEvent.click(screen.getByRole('button', {name: 'submit'}));

		expect(onSubmit.mock.calls[0]?.[0]).toEqual(expect.objectContaining({hasRetriesLeft: true}));
	});

	it('should open the date range modal for the selected date filter only', async () => {
		const screen = await render(<Harness />);

		await userEvent.click(screen.getByRole('button', {name: 'More Filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'End date range'}));

		await expect.element(screen.getByTestId('date-range-modal')).toBeVisible();
		await expect.element(screen.getByText('Filter by end date')).toBeVisible();
	});

	it('should clear keys, run the custom onRemove and submit when removing a filter', async () => {
		const onSubmit = vi.fn();
		const onRemoveCustom = vi.fn();
		const screen = await render(
			<Harness initialVisible={['custom']} onSubmit={onSubmit} onRemoveCustom={onRemoveCustom} />,
		);

		await userEvent.click(screen.getByRole('button', {name: 'Remove Custom filter Filter'}));

		await expect.element(screen.getByText('custom content')).not.toBeInTheDocument();
		expect(onRemoveCustom).toHaveBeenCalledTimes(1);
		expect(onSubmit).toHaveBeenCalledTimes(1);
	});
});
