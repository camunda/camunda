/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Form} from 'react-final-form';
import {render} from 'vitest-browser-react';
import {describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {FilterMultiSelect} from './FilterMultiSelect';

function getWrapper(initialValues?: {operationType?: string[] | string}) {
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

		await expect.element(screen.getByText('Create')).toBeVisible();
	});

	it('should reflect pre-selected items supplied as a comma-separated string', async () => {
		const screen = await render(
			<FilterMultiSelect name="operationType" titleText="Operation type" items={['CREATE', 'UPDATE', 'DELETE']} />,
			{wrapper: getWrapper({operationType: 'CREATE,UPDATE'})},
		);

		await expect.element(screen.getByText('Create')).toBeVisible();
		await expect.element(screen.getByText('Update')).toBeVisible();
	});

	it('should update the form value when an item is selected', async () => {
		let formValue: unknown;
		const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
			<Form
				onSubmit={() => {}}
				subscription={{values: true}}
				render={({handleSubmit, values}) => {
					formValue = (values as {operationType?: unknown}).operationType;
					return <form onSubmit={handleSubmit}>{children}</form>;
				}}
			>
				{children}
			</Form>
		);

		const screen = await render(
			<FilterMultiSelect name="operationType" titleText="Operation type" items={['CREATE', 'UPDATE']} />,
			{wrapper: Wrapper},
		);

		await screen.getByRole('combobox', {name: 'Operation type'}).click();
		await userEvent.click(screen.getByRole('option', {name: 'Create'}).element());

		expect(formValue).toEqual(['CREATE']);
	});

	it('should clear the form value to undefined when the last selected item is removed', async () => {
		let formValue: unknown = 'not-set-yet';
		const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
			<Form
				onSubmit={() => {}}
				initialValues={{operationType: ['CREATE']}}
				subscription={{values: true}}
				render={({handleSubmit, values}) => {
					formValue = (values as {operationType?: unknown}).operationType;
					return <form onSubmit={handleSubmit}>{children}</form>;
				}}
			>
				{children}
			</Form>
		);

		const screen = await render(
			<FilterMultiSelect name="operationType" titleText="Operation type" items={['CREATE', 'UPDATE']} />,
			{wrapper: Wrapper},
		);

		await userEvent.click(screen.getByRole('button', {name: /remove/i}).element());

		expect(formValue).toBeUndefined();
	});
});
