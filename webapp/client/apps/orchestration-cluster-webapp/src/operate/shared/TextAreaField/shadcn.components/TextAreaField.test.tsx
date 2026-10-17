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
import {TextAreaField} from './TextAreaField';

type FormValues = {
	description?: string;
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

describe('<TextAreaField />', () => {
	it('should render with a label and value', async () => {
		const screen = await render(
			<Field<string | undefined> name="description">
				{({input}) => <TextAreaField {...input} aria-label="Description" />}
			</Field>,
			{wrapper: getWrapper({initialValues: {description: 'order-123 details'}})},
		);

		await expect.element(screen.getByLabelText('Description')).toHaveValue('order-123 details');
	});

	it('should surface the invalid state and error message from the form field error', async () => {
		const screen = await render(
			<Field<string | undefined> name="description">
				{({input}) => <TextAreaField {...input} aria-label="Description" />}
			</Field>,
			{
				wrapper: getWrapper({
					validate: ({description}) => (description ? {} : {description: 'Description is required'}),
				}),
			},
		);

		const textarea = screen.getByLabelText('Description');

		await userEvent.type(textarea.element(), 'a');
		await userEvent.clear(textarea.element());
		await textarea.element().blur();

		await expect.element(textarea).toHaveAttribute('aria-invalid', 'true');
		await expect.element(screen.getByText('Description is required')).toBeVisible();
	});

	it('should forward a ref to the underlying textarea element', async () => {
		const ref = {current: null as HTMLTextAreaElement | null};
		await render(<TextAreaField ref={ref} name="description" aria-label="Description" />, {
			wrapper: getWrapper(),
		});

		expect(ref.current).toBeInstanceOf(HTMLTextAreaElement);
	});
});
