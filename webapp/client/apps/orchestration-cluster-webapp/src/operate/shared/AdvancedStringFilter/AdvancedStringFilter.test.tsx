/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Form} from 'react-final-form';
import {render} from 'vitest-browser-react';
import {describe, it, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {AdvancedStringFilter} from './AdvancedStringFilter';

type SubmittedValues = {businessId?: string};

function getWrapper({
	onSubmit = vi.fn(),
	initialValues,
}: {
	onSubmit?: (values: SubmittedValues) => void;
	initialValues?: SubmittedValues;
} = {}) {
	const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
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
}

describe('<AdvancedStringFilter />', () => {
	it('renders dropdown and input with the first operator selected by default', async () => {
		const screen = await render(
			<AdvancedStringFilter name="businessId" label="Business ID" selectableOperators={['$eq', '$like', '$in']} />,
			{wrapper: getWrapper()},
		);

		await expect.element(screen.getByRole('combobox', {name: 'Business ID filter type'})).toHaveTextContent('equals');
		await expect.element(screen.getByLabelText('Business ID', {exact: true})).toBeVisible();
	});

	it('decodes the initial form value into operator + value', async () => {
		const screen = await render(
			<AdvancedStringFilter name="businessId" label="Business ID" selectableOperators={['$eq', '$like', '$in']} />,
			{wrapper: getWrapper({initialValues: {businessId: 'like_order-123'}})},
		);

		await expect.element(screen.getByRole('combobox')).toHaveTextContent('contains');
		await expect.element(screen.getByLabelText('Business ID', {exact: true})).toHaveValue('order-123');
	});

	it('encodes the form value with the current operator when the user types', async () => {
		const onSubmit = vi.fn();
		const screen = await render(
			<AdvancedStringFilter name="businessId" label="Business ID" selectableOperators={['$eq', '$like', '$in']} />,
			{wrapper: getWrapper({onSubmit})},
		);

		await userEvent.type(screen.getByLabelText('Business ID', {exact: true}).element(), 'abc');
		await screen.getByRole('button', {name: 'submit'}).click();

		expect(onSubmit).toHaveBeenCalledWith(
			expect.objectContaining({businessId: 'eq_abc'}),
			expect.anything(),
			expect.anything(),
		);
	});

	it('re-encodes the form value when the operator changes', async () => {
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

		// force: the listbox auto-scrolls to the already-selected item on open, which can push
		// other options outside the isolated test viewport. Keyboard navigation is reliable regardless.
		await screen.getByRole('combobox').click();
		await userEvent.keyboard('{ArrowDown}{Enter}');
		await screen.getByRole('button', {name: 'submit'}).click();

		expect(onSubmit).toHaveBeenCalledWith(
			expect.objectContaining({businessId: 'like_abc'}),
			expect.anything(),
			expect.anything(),
		);
	});

	it('clears the form value to undefined when the input is emptied', async () => {
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

		await userEvent.clear(screen.getByLabelText('Business ID', {exact: true}).element());
		await screen.getByRole('button', {name: 'submit'}).click();

		expect(onSubmit).toHaveBeenLastCalledWith({}, expect.anything(), expect.anything());
	});

	it('preserves the dropdown selection when the input is emptied', async () => {
		const screen = await render(
			<AdvancedStringFilter name="businessId" label="Business ID" selectableOperators={['$eq', '$like', '$in']} />,
			{wrapper: getWrapper({initialValues: {businessId: 'like_order-123'}})},
		);

		await userEvent.clear(screen.getByLabelText('Business ID', {exact: true}).element());

		await expect.element(screen.getByRole('combobox')).toHaveTextContent('contains');
	});

	it('preserves the dropdown selection across operator-only changes with an empty value', async () => {
		const screen = await render(
			<AdvancedStringFilter name="businessId" label="Business ID" selectableOperators={['$eq', '$like', '$in']} />,
			{wrapper: getWrapper()},
		);

		await screen.getByRole('combobox').click();
		await screen.getByRole('option', {name: 'is one of'}).click({force: true});

		await expect.element(screen.getByRole('combobox')).toHaveTextContent('is one of');
	});

	it('treats a malformed value as an empty input', async () => {
		const screen = await render(
			<AdvancedStringFilter name="businessId" label="Business ID" selectableOperators={['$eq', '$like', '$in']} />,
			{
				wrapper: getWrapper({
					initialValues: {businessId: 'plain-legacy-value'},
				}),
			},
		);

		await expect.element(screen.getByLabelText('Business ID', {exact: true})).toHaveValue('');
		await expect.element(screen.getByRole('combobox')).toHaveTextContent('equals');
	});
});
