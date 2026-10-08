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
import type {OptionalFilter, OptionalFilterValues} from '../optionalFilters';
import {OptionalFiltersFormGroup} from './OptionalFiltersFormGroup';

const Harness: React.FC<{
	initialValues?: OptionalFilterValues;
	initialVisible?: OptionalFilter[];
	onSubmit?: (values: OptionalFilterValues) => void;
}> = ({initialValues = {}, initialVisible = [], onSubmit = vi.fn()}) => {
	const [visibleFilters, setVisibleFilters] = useState<OptionalFilter[]>(initialVisible);

	return (
		<Form<OptionalFilterValues> onSubmit={onSubmit} initialValues={initialValues}>
			{({handleSubmit, values}) => (
				<form onSubmit={handleSubmit}>
					<OptionalFiltersFormGroup
						filters={values}
						visibleFilters={visibleFilters}
						onVisibleFilterChange={setVisibleFilters}
					/>
					<button type="submit">submit</button>
				</form>
			)}
		</Form>
	);
};

describe('<OptionalFiltersFormGroup /> (design system)', () => {
	it('should list all optional filters in the menu', async () => {
		const screen = await render(<Harness />);

		await userEvent.click(screen.getByRole('button', {name: 'More Filters'}));

		await expect.element(screen.getByRole('menuitem', {name: 'Decision Instance Key(s)'})).toBeVisible();
		await expect.element(screen.getByRole('menuitem', {name: 'Process Instance Key'})).toBeVisible();
		await expect.element(screen.getByRole('menuitem', {name: 'Business ID'})).toBeVisible();
		await expect.element(screen.getByRole('menuitem', {name: 'Evaluation Date Range'})).toBeVisible();
	});

	it('should show a filter selected from the menu and hide it from the menu', async () => {
		const screen = await render(<Harness />);

		await userEvent.click(screen.getByRole('button', {name: 'More Filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Process Instance Key'}));

		await expect.element(screen.getByLabelText('Process Instance Key')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'More Filters'}));
		await expect.element(screen.getByRole('menuitem', {name: 'Process Instance Key'})).not.toBeInTheDocument();
	});

	it('should show filters that are active in the initial values', async () => {
		const screen = await render(
			<Harness initialValues={{decisionEvaluationInstanceKey: '2251799813688001', businessId: 'eq_order-1'}} />,
		);

		await expect.element(screen.getByLabelText('Decision Instance Key(s)')).toHaveValue('2251799813688001');
		await expect.element(screen.getByLabelText('Business ID', {exact: true})).toHaveValue('order-1');
	});

	it.for([{evaluationDateFrom: '2024-01-01T00:00:00.000Z'}, {evaluationDateTo: '2024-01-02T00:00:00.000Z'}])(
		'should show the date range filter when only one bound is active in the initial values',
		async (initialValues) => {
			const screen = await render(<Harness initialValues={initialValues} />);

			await userEvent.click(screen.getByRole('button', {name: 'More Filters'}));
			await expect.element(screen.getByRole('menuitem', {name: 'Evaluation Date Range'})).not.toBeInTheDocument();
		},
	);

	it('should serialize entered values on submit', async () => {
		const onSubmit = vi.fn();
		const screen = await render(<Harness initialVisible={['processInstanceKey']} onSubmit={onSubmit} />);

		await userEvent.fill(screen.getByLabelText('Process Instance Key'), '2251799813688001');
		await userEvent.click(screen.getByRole('button', {name: 'submit'}));

		expect(onSubmit).toHaveBeenCalledWith(
			expect.objectContaining({processInstanceKey: '2251799813688001'}),
			expect.anything(),
			expect.anything(),
		);
	});

	it('should show a validation error for invalid keys', async () => {
		const screen = await render(<Harness initialVisible={['processInstanceKey']} />);

		await userEvent.fill(screen.getByLabelText('Process Instance Key'), 'abc');
		await userEvent.tab();

		await expect.element(screen.getByLabelText('Process Instance Key')).toHaveAttribute('aria-invalid', 'true');
	});

	it('should remove a filter, clear its value and submit', async () => {
		const onSubmit = vi.fn();
		const screen = await render(
			<Harness
				initialValues={{processInstanceKey: '2251799813688001'}}
				initialVisible={['processInstanceKey']}
				onSubmit={onSubmit}
			/>,
		);

		await userEvent.click(screen.getByRole('button', {name: 'Remove Process Instance Key Filter'}));

		await expect.element(screen.getByLabelText('Process Instance Key')).not.toBeInTheDocument();
		expect(onSubmit).toHaveBeenCalledTimes(1);
		expect(onSubmit.mock.calls[0]?.[0].processInstanceKey).toBeUndefined();
	});

	it('should open the date range modal when selecting the evaluation date range', async () => {
		const screen = await render(<Harness />);

		await userEvent.click(screen.getByRole('button', {name: 'More Filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Evaluation Date Range'}));

		await expect.element(screen.getByTestId('date-range-modal')).toBeVisible();
	});
});
