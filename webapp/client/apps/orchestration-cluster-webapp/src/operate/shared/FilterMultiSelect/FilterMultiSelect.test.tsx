/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Form} from 'react-final-form';
import {render} from 'vitest-browser-react';
import {describe, expect, it} from 'vitest';
import {FilterMultiSelect} from './FilterMultiSelect';

function getWrapper(initialValues?: {operationType?: string[]}) {
	const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
		<Form onSubmit={() => {}} initialValues={initialValues}>
			{({handleSubmit}) => <form onSubmit={handleSubmit}>{children}</form>}
		</Form>
	);

	return Wrapper;
}

describe('<FilterMultiSelect />', () => {
	it('should render the provided items', async () => {
		const screen = await render(
			<FilterMultiSelect name="operationType" titleText="Operation type" items={['CREATE', 'UPDATE', 'DELETE']} />,
			{wrapper: getWrapper()},
		);

		await screen.getByRole('combobox', {name: 'Operation type'}).click();

		await expect.element(screen.getByRole('option', {name: 'Create'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: 'Update'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: 'Delete'})).toBeVisible();
	});

	it('should reflect pre-selected items from initial form values', async () => {
		const screen = await render(
			<FilterMultiSelect name="operationType" titleText="Operation type" items={['CREATE', 'UPDATE', 'DELETE']} />,
			{wrapper: getWrapper({operationType: ['CREATE']})},
		);

		await expect.element(screen.getByTitle('1')).toBeVisible();
	});

	it('should update the form value when an item is selected', async () => {
		const screen = await render(
			<FilterMultiSelect name="operationType" titleText="Operation type" items={['CREATE', 'UPDATE']} />,
			{wrapper: getWrapper()},
		);

		await screen.getByRole('combobox', {name: 'Operation type'}).click();
		await screen.getByRole('option', {name: 'Create'}).click();

		await expect.element(screen.getByTitle('1')).toBeVisible();
	});
});
