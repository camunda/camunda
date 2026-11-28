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
import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
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
		<Form<OptionalFilterValues> onSubmit={(values) => onSubmit(values)} initialValues={initialValues}>
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

		await userEvent.click(screen.getByRole('button', {name: 'More filters'}));

		await expect.element(screen.getByRole('menuitem', {name: 'Decision instance key(s)'})).toBeVisible();
		await expect.element(screen.getByRole('menuitem', {name: 'Process instance key'})).toBeVisible();
		await expect.element(screen.getByRole('menuitem', {name: 'Business ID'})).toBeVisible();
		await expect.element(screen.getByRole('menuitem', {name: 'Evaluation date range'})).toBeVisible();
	});

	it('should show a filter selected from the menu and hide it from the menu', async () => {
		const screen = await render(<Harness />);

		await userEvent.click(screen.getByRole('button', {name: 'More filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Process instance key'}));

		await expect.element(screen.getByLabelText('Process instance key')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'More filters'}));
		await expect.element(screen.getByRole('menuitem', {name: 'Process instance key'})).not.toBeInTheDocument();
	});

	it('should show filters that are active in the initial values', async () => {
		const screen = await render(
			<Harness initialValues={{decisionEvaluationInstanceKey: '2251799813688001', businessId: 'eq_order-1'}} />,
		);

		await expect.element(screen.getByLabelText('Decision instance key(s)')).toHaveValue('2251799813688001');
		await expect.element(screen.getByLabelText('Business ID', {exact: true})).toHaveValue('order-1');
	});

	it.for([{evaluationDateFrom: '2024-01-01T00:00:00.000Z'}, {evaluationDateTo: '2024-01-02T00:00:00.000Z'}])(
		'should show the date range filter when only one bound is active in the initial values',
		async (initialValues) => {
			const screen = await render(<Harness initialValues={initialValues} />);

			await userEvent.click(screen.getByRole('button', {name: 'More filters'}));
			await expect.element(screen.getByRole('menuitem', {name: 'Evaluation date range'})).not.toBeInTheDocument();
		},
	);

	it('should serialize entered values on submit', async () => {
		const onSubmit = vi.fn();
		const screen = await render(<Harness initialVisible={['processInstanceKey']} onSubmit={onSubmit} />);

		await userEvent.fill(screen.getByLabelText('Process instance key'), '2251799813688001');
		await userEvent.click(screen.getByRole('button', {name: 'submit'}));

		expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({processInstanceKey: '2251799813688001'}));
	});

	it('should show a validation error for invalid keys', async () => {
		const screen = await render(<Harness initialVisible={['processInstanceKey']} />);

		await userEvent.fill(screen.getByLabelText('Process instance key'), 'abc');
		await userEvent.tab();

		await expect.element(screen.getByLabelText('Process instance key')).toHaveAttribute('aria-invalid', 'true');
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

		await userEvent.click(screen.getByRole('button', {name: 'Remove Process instance key filter'}));

		await expect.element(screen.getByLabelText('Process instance key')).not.toBeInTheDocument();
		expect(onSubmit).toHaveBeenCalledTimes(1);
		expect(onSubmit).toHaveBeenCalledWith(expect.not.objectContaining({processInstanceKey: expect.anything()}));
	});

	it('should show the date range picker when selecting the evaluation date range', async () => {
		const screen = await render(<Harness />);

		await userEvent.click(screen.getByRole('button', {name: 'More filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Evaluation date range'}));

		await expect.element(screen.getByRole('button', {name: /^Evaluation date range/})).toBeVisible();
	});

	it('should not submit and should show an error when the end is before the start', async () => {
		const onSubmit = vi.fn();
		const screen = await render(
			<Harness
				initialVisible={['evaluationDateRange']}
				initialValues={{
					evaluationDateFrom: '2026-10-14T00:00:00.000+00:00',
					evaluationDateTo: '2026-10-07T00:00:00.000+00:00',
				}}
				onSubmit={onSubmit}
			/>,
		);

		await userEvent.click(screen.getByRole('button', {name: 'submit'}));

		expect(onSubmit).not.toHaveBeenCalled();
		await expect
			.element(screen.getByRole('alert').filter({hasText: 'The end must not be before the start'}))
			.toBeInTheDocument();
	});

	it('should submit a range that has only a start', async () => {
		const onSubmit = vi.fn();
		const screen = await render(
			<Harness
				initialVisible={['evaluationDateRange']}
				initialValues={{evaluationDateFrom: '2026-10-07T00:00:00.000+00:00'}}
				onSubmit={onSubmit}
			/>,
		);

		await userEvent.click(screen.getByRole('button', {name: 'submit'}));

		expect(onSubmit).toHaveBeenCalledWith(
			expect.objectContaining({evaluationDateFrom: '2026-10-07T00:00:00.000+00:00'}),
		);
	});

	it.for([
		{key: 'evaluationDateFrom', value: 'not-a-date'},
		{key: 'evaluationDateFrom', value: '2026-10-07'},
		{key: 'evaluationDateTo', value: '20261014T000000Z'},
	])('should not submit $key when it is $value', async ({key, value}) => {
		const onSubmit = vi.fn();
		const screen = await render(
			<Harness initialVisible={['evaluationDateRange']} initialValues={{[key]: value}} onSubmit={onSubmit} />,
		);

		await userEvent.click(screen.getByRole('button', {name: 'submit'}));

		expect(onSubmit).toHaveBeenCalledWith(expect.not.objectContaining({[key]: value}));
	});

	it('should submit a custom range with exact bounds including seconds', async () => {
		const onSubmit = vi.fn();
		const screen = await render(
			<Harness
				initialVisible={['evaluationDateRange']}
				initialValues={{
					evaluationDateFrom: '2026-10-07T00:00:00.000+00:00',
					evaluationDateTo: '2026-10-14T00:00:00.000+00:00',
				}}
				onSubmit={onSubmit}
			/>,
		);

		await userEvent.click(screen.getByRole('button', {name: /^Evaluation date range/}));
		await userEvent.fill(screen.getByLabelText('From time'), '08:30:15');
		await userEvent.fill(screen.getByLabelText('To time'), '17:45:30');
		await userEvent.click(screen.getByRole('button', {name: 'Apply'}));
		await userEvent.click(screen.getByRole('button', {name: 'submit'}));

		expect(onSubmit).toHaveBeenCalledWith(
			expect.objectContaining({
				evaluationDateFrom: expect.stringMatching(/T08:30:15\.000/),
				evaluationDateTo: expect.stringMatching(/T17:45:30\.000/),
			}),
		);
	});

	describe('with a fixed current date', () => {
		beforeEach(() => {
			vi.useFakeTimers({toFake: ['Date'], now: new Date(2026, 9, 15, 12, 0, 0, 0)});
		});

		afterEach(() => {
			vi.useRealTimers();
		});

		it('should fill the evaluation date range when selecting a quick range', async () => {
			const onSubmit = vi.fn();
			const screen = await render(<Harness initialVisible={['evaluationDateRange']} onSubmit={onSubmit} />);

			await userEvent.click(screen.getByRole('button', {name: /^Evaluation date range/}));
			await userEvent.click(screen.getByRole('button', {name: 'Last 3 hours'}));
			await userEvent.click(screen.getByRole('button', {name: 'Apply'}));
			await userEvent.click(screen.getByRole('button', {name: 'submit'}));

			expect(onSubmit).toHaveBeenCalledWith(
				expect.objectContaining({
					evaluationDateFrom: expect.stringMatching(/^2026-10-15T\d{2}:\d{2}:\d{2}\.\d{3}[+-]\d{4}$/),
					evaluationDateTo: expect.stringMatching(/^2026-10-15T\d{2}:\d{2}:\d{2}\.\d{3}[+-]\d{4}$/),
				}),
			);
		});

		it('should submit a range picked from the calendar starting empty', async () => {
			const onSubmit = vi.fn();
			const screen = await render(<Harness initialVisible={['evaluationDateRange']} onSubmit={onSubmit} />);

			await userEvent.click(screen.getByRole('button', {name: /^Evaluation date range/}));
			await userEvent.click(screen.getByRole('button', {name: /October 10(st|nd|rd|th)/}));
			await userEvent.click(screen.getByRole('button', {name: /October 20(st|nd|rd|th)/}));
			await userEvent.click(screen.getByRole('button', {name: 'Apply'}));
			await userEvent.click(screen.getByRole('button', {name: 'submit'}));

			expect(onSubmit).toHaveBeenCalledWith(
				expect.objectContaining({
					evaluationDateFrom: expect.stringMatching(/^2026-10-10T00:00:00\.000/),
					evaluationDateTo: expect.stringMatching(/^2026-10-20T23:59:59\.000/),
				}),
			);
		});
	});

	it('should keep the submitted range when cancelling draft edits', async () => {
		const onSubmit = vi.fn();
		const initialValues = {
			evaluationDateFrom: '2026-10-07T08:30:15.000+00:00',
			evaluationDateTo: '2026-10-14T17:45:00.000+00:00',
		};
		const screen = await render(
			<Harness initialVisible={['evaluationDateRange']} initialValues={initialValues} onSubmit={onSubmit} />,
		);

		await userEvent.click(screen.getByRole('button', {name: /^Evaluation date range/}));
		await userEvent.click(screen.getByRole('button', {name: 'Last 3 hours'}));
		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}));
		await userEvent.click(screen.getByRole('button', {name: 'submit'}));

		expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining(initialValues));
	});

	it('should clear both bounds when clearing a populated range', async () => {
		const onSubmit = vi.fn();
		const screen = await render(
			<Harness
				initialVisible={['evaluationDateRange']}
				initialValues={{
					evaluationDateFrom: '2026-10-07T08:30:15.000+00:00',
					evaluationDateTo: '2026-10-14T17:45:00.000+00:00',
				}}
				onSubmit={onSubmit}
			/>,
		);

		await userEvent.click(screen.getByRole('button', {name: 'Clear date and time range'}));
		await userEvent.click(screen.getByRole('button', {name: 'submit'}));

		expect(onSubmit).toHaveBeenCalledWith(expect.not.objectContaining({evaluationDateFrom: expect.anything()}));
		expect(onSubmit).toHaveBeenCalledWith(expect.not.objectContaining({evaluationDateTo: expect.anything()}));
	});

	it('should show the existing evaluation date range', async () => {
		const evaluationDateFrom = new Date(2026, 9, 7, 8, 30, 15, 0).toISOString();
		const evaluationDateTo = new Date(2026, 9, 14, 17, 45, 0, 0).toISOString();
		const screen = await render(
			<Harness
				initialVisible={['evaluationDateRange']}
				initialValues={{
					evaluationDateFrom,
					evaluationDateTo,
				}}
			/>,
		);

		await expect
			.element(screen.getByRole('button', {name: /^Evaluation date range/}))
			.toMatchTextContent(/Oct\s7,\s2026.*Oct\s14,\s2026/);
	});
});
