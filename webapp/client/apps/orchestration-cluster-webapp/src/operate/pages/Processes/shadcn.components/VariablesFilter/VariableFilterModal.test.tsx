/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {setVariableConditions} from '../../VariablesFilter/variableFilterStore';
import {VariableFilterModal} from './VariableFilterModal';

const PATH = '/operate-preview/processes/filters/variables';

async function renderModal() {
	const screen = await renderWithRouter(VariableFilterModal, {path: PATH});
	return {...screen, screen, dialog: screen.getByRole('dialog')};
}

describe('<VariableFilterModal /> (design system)', () => {
	afterEach(() => {
		sessionStorage.clear();
	});

	it('should expose the Fields content as a labelled tab panel', async () => {
		const {dialog} = await renderModal();

		await expect.element(dialog.getByRole('tab', {name: 'Fields'})).toHaveAttribute('aria-selected', 'true');
		await expect.element(dialog.getByRole('tabpanel', {name: 'Fields'})).toBeVisible();
	});

	it('should show row errors only after Apply', async () => {
		const {dialog} = await renderModal();

		await expect.element(dialog.getByText('Variable name is required')).not.toBeInTheDocument();
		await userEvent.click(dialog.getByRole('button', {name: 'Apply'}));

		await expect.element(dialog.getByText('Variable name is required')).toBeVisible();
		await expect.element(dialog.getByText('Value is required')).toBeVisible();
		await expect.element(dialog).toBeVisible();
	});

	it('should apply valid conditions and show them when the modal is reopened', async () => {
		const {dialog, router, screen, unmount} = await renderModal();

		await userEvent.fill(dialog.getByRole('textbox', {name: 'Name'}), 'status');
		await userEvent.fill(dialog.getByRole('textbox', {name: 'Value'}), '"open"');
		await userEvent.click(dialog.getByRole('button', {name: 'Add condition'}));
		await userEvent.fill(dialog.getByRole('textbox', {name: 'Name'}).last(), 'retries');
		await userEvent.click(dialog.getByRole('combobox', {name: 'Operator'}).last());
		await userEvent.click(screen.getByRole('option', {name: 'does not exist'}));
		await userEvent.click(dialog.getByRole('button', {name: 'Apply'}));
		await expect.poll(() => router.state.location.pathname).toMatch(/\/processes$/);
		await unmount();
		const reopened = await renderModal();

		await expect.element(reopened.dialog.getByRole('textbox', {name: 'Name'}).first()).toHaveValue('status');
		await expect.element(reopened.dialog.getByRole('textbox', {name: 'Value'})).toHaveValue('"open"');
		await expect.element(reopened.dialog.getByRole('textbox', {name: 'Name'}).last()).toHaveValue('retries');
	});

	it('should keep the stored conditions when the modal is cancelled after editing', async () => {
		setVariableConditions([
			{name: 'status', operator: 'equals', value: '"open"'},
			{name: 'retries', operator: 'exists', value: ''},
		]);
		const {dialog, router, unmount} = await renderModal();

		await userEvent.click(dialog.getByRole('button', {name: 'Remove condition'}).first());
		await userEvent.click(dialog.getByRole('button', {name: 'Cancel'}));
		await expect.poll(() => router.state.location.pathname).toMatch(/\/processes$/);
		await unmount();
		const reopened = await renderModal();

		await expect.element(reopened.dialog.getByRole('textbox', {name: 'Name'}).first()).toHaveValue('status');
		await expect.element(reopened.dialog.getByRole('textbox', {name: 'Name'}).last()).toHaveValue('retries');
	});

	it('should keep the remaining rows intact when a middle row is removed', async () => {
		setVariableConditions([
			{name: 'first', operator: 'equals', value: '1'},
			{name: 'second', operator: 'equals', value: '2'},
			{name: 'third', operator: 'equals', value: '3'},
		]);
		const {dialog} = await renderModal();

		await userEvent.click(dialog.getByRole('button', {name: 'Remove condition'}).nth(1));

		await expect.element(dialog.getByRole('textbox', {name: 'Name'}).first()).toHaveValue('first');
		await expect.element(dialog.getByRole('textbox', {name: 'Name'}).last()).toHaveValue('third');
		await expect.element(dialog.getByRole('textbox', {name: 'Value'}).last()).toHaveValue('3');
		await expect.element(dialog.getByRole('textbox', {name: 'Name'}).nth(2)).not.toBeInTheDocument();
	});
});
