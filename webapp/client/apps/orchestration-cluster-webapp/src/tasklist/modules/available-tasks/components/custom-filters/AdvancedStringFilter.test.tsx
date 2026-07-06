/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {Form} from 'react-final-form';
import {AdvancedStringFilter} from './AdvancedStringFilter';

type SubmittedValues = {businessId?: string};

const getWrapper = ({
	onSubmit = vi.fn(),
	initialValues,
}: {
	onSubmit?: (values: SubmittedValues) => void;
	initialValues?: SubmittedValues;
} = {}) => {
	const Wrapper = ({children}: {children: React.ReactNode}) => (
		<Form<SubmittedValues> onSubmit={onSubmit} initialValues={initialValues}>
			{({handleSubmit}) => (
				<form onSubmit={handleSubmit}>
					{children}
					<button type="submit">submit</button>
				</form>
			)}
		</Form>
	);
	return Wrapper;
};

describe('<AdvancedStringFilter />', () => {
	it('should render dropdown and input with the first operator selected by default', async () => {
		const screen = await render(
			<AdvancedStringFilter name="businessId" label="Business ID" selectableOperators={['$eq', '$like', '$in']} />,
			{wrapper: getWrapper()},
		);

		await expect.element(screen.getByRole('combobox', {name: 'Business ID filter type'})).toHaveTextContent('equals');
		await expect.element(screen.getByRole('textbox', {name: 'Business ID'})).toBeInTheDocument();
	});

	it('should decode the initial form value into operator + value', async () => {
		const screen = await render(
			<AdvancedStringFilter name="businessId" label="Business ID" selectableOperators={['$eq', '$like', '$in']} />,
			{wrapper: getWrapper({initialValues: {businessId: 'like_order-123'}})},
		);

		await expect.element(screen.getByRole('combobox')).toHaveTextContent('contains');
		await expect.element(screen.getByRole('textbox', {name: 'Business ID'})).toHaveValue('order-123');
	});

	it('should encode the form value with the current operator when the user types', async () => {
		const onSubmit = vi.fn();
		const screen = await render(
			<AdvancedStringFilter name="businessId" label="Business ID" selectableOperators={['$eq', '$like', '$in']} />,
			{wrapper: getWrapper({onSubmit})},
		);

		await userEvent.fill(screen.getByRole('textbox', {name: 'Business ID'}), 'abc');
		await userEvent.click(screen.getByRole('button', {name: 'submit'}));

		expect(onSubmit).toHaveBeenCalledWith(
			expect.objectContaining({businessId: 'eq_abc'}),
			expect.anything(),
			expect.anything(),
		);
	});

	it('should re-encode the form value when the operator changes', async () => {
		const onSubmit = vi.fn();
		const screen = await render(
			<AdvancedStringFilter name="businessId" label="Business ID" selectableOperators={['$eq', '$like', '$in']} />,
			{
				wrapper: getWrapper({
					onSubmit,
					initialValues: {businessId: 'eq_abc'},
				}),
			},
		);

		await userEvent.click(screen.getByRole('combobox'));
		await userEvent.keyboard('{ArrowDown}{Enter}');
		await userEvent.click(screen.getByRole('button', {name: 'submit'}));

		expect(onSubmit).toHaveBeenCalledWith(
			expect.objectContaining({businessId: 'like_abc'}),
			expect.anything(),
			expect.anything(),
		);
	});

	it('should clear the form value to undefined when the input is emptied', async () => {
		const onSubmit = vi.fn();
		const screen = await render(
			<AdvancedStringFilter name="businessId" label="Business ID" selectableOperators={['$eq', '$like', '$in']} />,
			{
				wrapper: getWrapper({
					onSubmit,
					initialValues: {businessId: 'eq_abc'},
				}),
			},
		);

		await userEvent.clear(screen.getByRole('textbox', {name: 'Business ID'}));
		await userEvent.click(screen.getByRole('button', {name: 'submit'}));

		expect(onSubmit).toHaveBeenLastCalledWith({}, expect.anything(), expect.anything());
	});

	it('should preserve the dropdown selection when the input is emptied', async () => {
		const screen = await render(
			<AdvancedStringFilter name="businessId" label="Business ID" selectableOperators={['$eq', '$like', '$in']} />,
			{wrapper: getWrapper({initialValues: {businessId: 'like_order-123'}})},
		);

		await userEvent.clear(screen.getByRole('textbox', {name: 'Business ID'}));

		await expect.element(screen.getByRole('combobox')).toHaveTextContent('contains');
	});

	it('should preserve the dropdown selection across operator-only changes with an empty value', async () => {
		const screen = await render(
			<AdvancedStringFilter name="businessId" label="Business ID" selectableOperators={['$eq', '$like', '$in']} />,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('combobox'));
		await userEvent.keyboard('{ArrowDown}{ArrowDown}{Enter}');

		await expect.element(screen.getByRole('combobox')).toHaveTextContent('is one of');
	});

	it('should treat a malformed value as an empty input', async () => {
		const screen = await render(
			<AdvancedStringFilter name="businessId" label="Business ID" selectableOperators={['$eq', '$like', '$in']} />,
			{
				wrapper: getWrapper({
					initialValues: {businessId: 'plain-legacy-value'},
				}),
			},
		);

		await expect.element(screen.getByRole('textbox', {name: 'Business ID'})).toHaveValue('');
		await expect.element(screen.getByRole('combobox')).toHaveTextContent('equals');
	});
});
