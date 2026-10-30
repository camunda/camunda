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
import {VariableFilterModal} from './VariableFilterModal';

const STORAGE_KEY = 'operate.variableFilter.conditions';
const PATH = '/operate-preview/processes/filters/variables';

function getStoredConditions(): unknown {
	return JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? 'null');
}

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
		expect(getStoredConditions()).toBeNull();
	});

	it('should apply valid conditions and close', async () => {
		const {dialog, router, screen} = await renderModal();

		await userEvent.fill(dialog.getByRole('textbox', {name: 'Name'}), 'status');
		await userEvent.fill(dialog.getByRole('textbox', {name: 'Value'}), '"open"');
		await userEvent.click(dialog.getByRole('button', {name: 'Add condition'}));
		await userEvent.fill(dialog.getByRole('textbox', {name: 'Name'}).last(), 'retries');
		await userEvent.click(dialog.getByRole('combobox', {name: 'Operator'}).last());
		await userEvent.click(screen.getByRole('option', {name: 'does not exist'}));
		await userEvent.click(dialog.getByRole('button', {name: 'Apply'}));

		await expect.poll(() => router.state.location.pathname).toBe('/operate-preview/processes');
		expect(getStoredConditions()).toEqual([
			{name: 'status', operator: 'equals', value: '"open"'},
			{name: 'retries', operator: 'doesNotExist', value: ''},
		]);
	});

	it('should close on Cancel without changing the stored conditions', async () => {
		const conditions = [
			{name: 'status', operator: 'equals', value: '"open"'},
			{name: 'retries', operator: 'exists', value: ''},
		];
		sessionStorage.setItem(STORAGE_KEY, JSON.stringify(conditions));
		const {dialog, router} = await renderModal();

		await userEvent.click(dialog.getByRole('button', {name: 'Remove condition'}).first());
		await userEvent.click(dialog.getByRole('button', {name: 'Cancel'}));

		await expect.poll(() => router.state.location.pathname).toBe('/operate-preview/processes');
		expect(getStoredConditions()).toEqual(conditions);
	});
});
