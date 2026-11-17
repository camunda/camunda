/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {Field, Form} from 'react-final-form';
import {render} from 'vitest-browser-react';
import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {TextInputField} from './TextInputField';

type FormValues = {
	businessId?: string;
};

function getWrapper({
	onSubmit = vi.fn(),
	validate,
	initialValues,
}: {
	onSubmit?: (values: FormValues) => void;
	validate?: (values: FormValues) => Partial<Record<keyof FormValues, string>>;
	initialValues?: FormValues;
} = {}) {
	const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
		<Form<FormValues> onSubmit={onSubmit} validate={validate} initialValues={initialValues}>
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

describe('<TextInputField />', () => {
	it('should render with a label and value', async () => {
		const screen = await render(
			<Field<string | undefined> name="businessId">
				{({input}) => <TextInputField {...input} aria-label="Business ID" />}
			</Field>,
			{wrapper: getWrapper({initialValues: {businessId: 'order-123'}})},
		);

		await expect.element(screen.getByLabelText('Business ID')).toHaveValue('order-123');
	});

	it('should surface the invalid state and error message from the form field error', async () => {
		const screen = await render(
			<Field<string | undefined> name="businessId">
				{({input}) => <TextInputField {...input} aria-label="Business ID" />}
			</Field>,
			{
				wrapper: getWrapper({
					validate: ({businessId}) => (businessId ? {} : {businessId: 'Business ID is required'}),
				}),
			},
		);

		const input = screen.getByLabelText('Business ID');

		await userEvent.type(input.element(), 'a');
		await userEvent.clear(input.element());
		await input.element().blur();

		await expect.element(input).toHaveAttribute('aria-invalid', 'true');
		await expect.element(screen.getByText('Business ID is required')).toBeVisible();
	});

	it('should forward a ref to the underlying input element', async () => {
		const ref = {current: null as HTMLInputElement | null};
		await render(<TextInputField ref={ref} name="businessId" aria-label="Business ID" />, {
			wrapper: getWrapper(),
		});

		expect(ref.current).toBeInstanceOf(HTMLInputElement);
	});
});
