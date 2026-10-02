/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {userEvent, type Locator} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {VariableFilterModal} from './VariableFilterModal';

const STORAGE_KEY = 'operate.variableFilter.conditions';

function storeConditions(conditions: unknown[]) {
	sessionStorage.setItem(STORAGE_KEY, JSON.stringify(conditions));
}

function getStoredConditions(): unknown {
	return JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? 'null');
}

// Closing Monaco before its word highlighter fires leaves an unhandled "Canceled" rejection.
function settleEditor() {
	return vi.advanceTimersByTimeAsync(300);
}

// Monaco loads lazily; slower runners need longer than the default element timeout.
async function waitForEditor(dialog: Locator, name: string) {
	const editor = dialog.getByRole('textbox', {name});
	await expect.element(editor, {timeout: 15_000}).toBeInTheDocument();
	return editor;
}

async function renderModal() {
	const screen = await renderWithRouter(VariableFilterModal, {path: '/operate/processes/filters/variables'});
	return {...screen, dialog: screen.getByRole('dialog')};
}

describe('<VariableFilterModal />', () => {
	beforeEach(() => {
		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout'], shouldAdvanceTime: true});
	});

	afterEach(() => {
		vi.useRealTimers();
		sessionStorage.clear();
	});

	it('should start with one empty row whose remove button is hidden', async () => {
		const {dialog} = await renderModal();

		await expect.element(dialog.getByRole('heading', {name: 'Filter by variable'})).toBeVisible();
		await expect.element(dialog.getByRole('textbox', {name: 'Name'})).toHaveValue('');
		await expect.element(dialog.getByRole('combobox', {name: 'Operator'})).toMatchTextContent(/^equals/);
		const removeButton = dialog.getByRole('button', {name: 'Remove condition', includeHidden: true});
		await expect.element(removeButton).toBeInTheDocument();
		await expect.element(removeButton).not.toBeVisible();

		await userEvent.click(dialog.getByRole('button', {name: 'Add condition'}));

		await expect.element(dialog.getByRole('button', {name: 'Remove condition'}).first()).toBeVisible();
	});

	it('should show row errors only after Apply and hide each one once its field is edited', async () => {
		const {dialog} = await renderModal();

		await expect.element(dialog.getByText('Variable name is required')).not.toBeInTheDocument();
		await userEvent.click(dialog.getByRole('button', {name: 'Apply'}));

		await expect.element(dialog.getByText('Variable name is required')).toBeVisible();
		await expect.element(dialog.getByText('Value is required')).toBeVisible();

		await userEvent.fill(dialog.getByRole('textbox', {name: 'Name'}), 'status');

		await expect.element(dialog.getByText('Variable name is required')).not.toBeInTheDocument();
		await expect.element(dialog.getByText('Value is required')).toBeVisible();
		expect(getStoredConditions()).toBeNull();
	});

	it('should hide a value error once the operator of that row changes', async () => {
		const {dialog} = await renderModal();

		await userEvent.fill(dialog.getByRole('textbox', {name: 'Name'}), 'status');
		await userEvent.fill(dialog.getByRole('textbox', {name: 'Value'}), '{broken');
		await userEvent.click(dialog.getByRole('button', {name: 'Apply'}));
		await expect.element(dialog.getByText('Invalid value: {broken')).toBeVisible();

		await userEvent.click(dialog.getByRole('combobox', {name: 'Operator'}));
		await userEvent.click(dialog.getByRole('option', {name: 'contains'}));

		await expect.element(dialog.getByRole('textbox', {name: 'Value'})).toHaveValue('{broken');
		await expect.element(dialog.getByText('Invalid value: {broken')).not.toBeInTheDocument();
	});

	it.for([
		{operator: 'equals', value: '"open', error: 'Invalid value: "open'},
		{operator: 'is one of', value: '[1, 2', error: 'Invalid value: [1, 2'},
	] as const)('should reject an unparseable $operator value on Apply', async ({operator, value, error}) => {
		const {dialog, router} = await renderModal();

		await userEvent.fill(dialog.getByRole('textbox', {name: 'Name'}), 'status');
		await userEvent.click(dialog.getByRole('combobox', {name: 'Operator'}));
		await userEvent.click(dialog.getByRole('option', {name: operator}));
		await userEvent.fill(dialog.getByRole('textbox', {name: 'Value'}), value);
		await userEvent.click(dialog.getByRole('button', {name: 'Apply'}));

		await expect.element(dialog.getByText(error)).toBeVisible();
		expect(router.state.location.pathname).toBe('/operate/processes/filters/variables');
		expect(getStoredConditions()).toBeNull();
	});

	it('should apply valid rows, including raw contains text and valueless operators, and close', async () => {
		const {dialog, router} = await renderModal();

		await userEvent.fill(dialog.getByRole('textbox', {name: 'Name'}), 'note');
		await userEvent.click(dialog.getByRole('combobox', {name: 'Operator'}));
		await userEvent.click(dialog.getByRole('option', {name: 'contains'}));
		await userEvent.fill(dialog.getByRole('textbox', {name: 'Value'}), '{"partial');
		await userEvent.click(dialog.getByRole('button', {name: 'Add condition'}));
		await userEvent.fill(dialog.getByRole('textbox', {name: 'Name'}).last(), 'retries');
		await userEvent.click(dialog.getByRole('combobox', {name: 'Operator'}).last());
		await userEvent.click(dialog.getByRole('option', {name: 'does not exist'}));
		await userEvent.click(dialog.getByRole('button', {name: 'Apply'}));

		await expect.poll(() => router.state.location.pathname).toBe('/operate/processes');
		expect(getStoredConditions()).toEqual([
			{name: 'note', operator: 'contains', value: '{"partial'},
			{name: 'retries', operator: 'doesNotExist', value: ''},
		]);
	});

	it('should close on Cancel without changing the stored conditions', async () => {
		const conditions = [
			{name: 'status', operator: 'equals', value: '"open"'},
			{name: 'retries', operator: 'exists', value: ''},
		];
		storeConditions(conditions);
		const {dialog, router} = await renderModal();

		await expect.element(dialog.getByRole('textbox', {name: 'Name'}).first()).toHaveValue('status');
		await userEvent.click(dialog.getByRole('button', {name: 'Remove condition'}).first());
		await expect.element(dialog.getByRole('textbox', {name: 'Name'})).toHaveValue('retries');
		await userEvent.click(dialog.getByRole('button', {name: 'Cancel'}));

		await expect.poll(() => router.state.location.pathname).toBe('/operate/processes');
		expect(getStoredConditions()).toEqual(conditions);
	});

	it('should add an empty row after a stored row was removed', async () => {
		storeConditions([
			{name: 'status', operator: 'equals', value: '"open"'},
			{name: 'retries', operator: 'exists', value: ''},
		]);
		const {dialog} = await renderModal();

		await userEvent.click(dialog.getByRole('button', {name: 'Remove condition'}).first());
		await userEvent.click(dialog.getByRole('button', {name: 'Add condition'}));

		await expect.element(dialog.getByRole('textbox', {name: 'Name'}).first()).toHaveValue('retries');
		await expect.element(dialog.getByRole('textbox', {name: 'Name'}).last()).toHaveValue('');
		await expect.element(dialog.getByRole('combobox', {name: 'Operator'}).last()).toMatchTextContent(/^equals/);
	});

	it('should warn about contains and about many conditions', async () => {
		const {dialog} = await renderModal();

		await userEvent.click(dialog.getByRole('combobox', {name: 'Operator'}));
		await userEvent.click(dialog.getByRole('option', {name: 'contains'}));

		await expect
			.element(
				dialog.getByText(
					'"contains" searches only the first ~8 000 characters of a variable value. Matches in longer values may not be returned.',
				),
			)
			.toBeVisible();

		for (let rows = 1; rows < 7; rows++) {
			await userEvent.click(dialog.getByRole('button', {name: 'Add condition'}));
		}
		await expect
			.element(dialog.getByText('Filtering by many conditions can be slow. Add conditions only if you need them.'))
			.not.toBeInTheDocument();
		await userEvent.click(dialog.getByRole('button', {name: 'Add condition'}));

		await expect
			.element(dialog.getByText('Filtering by many conditions can be slow. Add conditions only if you need them.'))
			.toBeVisible();
	});

	it('should check only the JSON shape when switching back to Fields and validate rows on Apply', async () => {
		const {dialog} = await renderModal();

		await userEvent.fill(dialog.getByRole('textbox', {name: 'Name'}), 'status');
		await userEvent.click(dialog.getByRole('tab', {name: 'JSON'}));
		await settleEditor();
		await userEvent.click(dialog.getByRole('tab', {name: 'Fields'}));

		await expect.element(dialog.getByRole('textbox', {name: 'Name'})).toHaveValue('status');
		await expect.element(dialog.getByText('Value is required')).not.toBeInTheDocument();
		await expect
			.element(
				dialog.getByText(
					'JSON could not be parsed. Switch back to the JSON tab to fix it. Existing conditions were kept.',
				),
			)
			.not.toBeInTheDocument();

		await userEvent.click(dialog.getByRole('tab', {name: 'JSON'}));
		await settleEditor();
		await userEvent.click(dialog.getByRole('button', {name: 'Apply'}));

		await expect.element(dialog.getByRole('alert')).toMatchTextContent('Could not apply JSON');
		await expect.element(dialog.getByRole('alert')).toMatchTextContent('Condition #1: Value is required');
	});

	it('should reject an unparseable is one of value applied from the JSON tab', async () => {
		const {dialog, router} = await renderModal();
		await userEvent.fill(dialog.getByRole('textbox', {name: 'Name'}), 'status');
		await userEvent.click(dialog.getByRole('combobox', {name: 'Operator'}));
		await userEvent.click(dialog.getByRole('option', {name: 'is one of'}));
		await userEvent.fill(dialog.getByRole('textbox', {name: 'Value'}), '{notjson');

		await userEvent.click(dialog.getByRole('tab', {name: 'JSON'}));
		await expect.element(await waitForEditor(dialog, 'JSON')).toBeInTheDocument();
		await settleEditor();
		await userEvent.click(dialog.getByRole('button', {name: 'Apply'}));

		await expect.element(dialog.getByRole('alert').filter({hasText: 'Could not apply JSON'})).toBeVisible();
		expect(router.state.location.pathname).toBe('/operate/processes/filters/variables');
		expect(getStoredConditions()).toBeNull();
		await settleEditor();
	});

	it('should keep the rows and warn when switching back to Fields from invalid JSON', async () => {
		storeConditions([{name: 'status', operator: 'equals', value: '"open"'}]);
		const {dialog} = await renderModal();

		await userEvent.click(dialog.getByRole('tab', {name: 'JSON'}));
		await expect.element(await waitForEditor(dialog, 'JSON')).toHaveFocus();
		await settleEditor();
		await userEvent.keyboard('x');
		await settleEditor();
		await userEvent.click(dialog.getByRole('button', {name: 'Apply'}));

		await expect.element(dialog.getByRole('alert').filter({hasText: 'Invalid JSON syntax'})).toBeVisible();
		await settleEditor();

		await userEvent.click(dialog.getByRole('tab', {name: 'Fields'}));

		await expect
			.element(
				dialog.getByText(
					'JSON could not be parsed. Switch back to the JSON tab to fix it. Existing conditions were kept.',
				),
			)
			.toBeVisible();
		await expect.element(dialog.getByRole('textbox', {name: 'Name'})).toHaveValue('status');
		expect(getStoredConditions()).toEqual([{name: 'status', operator: 'equals', value: '"open"'}]);
	});

	it('should apply typed conditions from the JSON tab', async () => {
		const {dialog, router} = await renderModal();

		await userEvent.fill(dialog.getByRole('textbox', {name: 'Name'}), 'total');
		await userEvent.fill(dialog.getByRole('textbox', {name: 'Value'}), '42');
		await userEvent.click(dialog.getByRole('tab', {name: 'JSON'}));
		await settleEditor();
		await userEvent.click(dialog.getByRole('button', {name: 'Apply'}));

		await expect.poll(() => router.state.location.pathname).toBe('/operate/processes');
		expect(getStoredConditions()).toEqual([{name: 'total', operator: 'equals', value: '42'}]);
	});

	it('should clear all conditions when an empty JSON list is applied', async () => {
		storeConditions([{name: 'status', operator: 'equals', value: '"open"'}]);
		const {dialog, router} = await renderModal();

		await userEvent.clear(dialog.getByRole('textbox', {name: 'Name'}));
		await userEvent.clear(dialog.getByRole('textbox', {name: 'Value'}));
		await userEvent.click(dialog.getByRole('tab', {name: 'JSON'}));
		await settleEditor();
		await userEvent.click(dialog.getByRole('button', {name: 'Apply'}));

		await expect.poll(() => router.state.location.pathname).toBe('/operate/processes');
		expect(getStoredConditions()).toBeNull();
	});

	it('should restore the value on Cancel and keep the formatted value on Save in the editor view', async () => {
		const name = 'n'.repeat(60);
		storeConditions([{name, operator: 'equals', value: '{"a":1}'}]);
		const {dialog} = await renderModal();

		await userEvent.click(dialog.getByRole('button', {name: 'Open JSON editor'}));

		await expect.element(dialog.getByRole('heading', {name: `Edit value: ${'n'.repeat(47)}...`})).toBeVisible();
		await expect.element(dialog.getByText('Filter by variable')).toBeVisible();
		await expect.element(await waitForEditor(dialog, 'Value')).toHaveFocus();
		await settleEditor();

		await userEvent.click(dialog.getByRole('button', {name: 'Cancel'}));

		await expect.element(dialog.getByRole('heading', {name: 'Filter by variable'})).toBeVisible();
		await expect.element(dialog.getByRole('textbox', {name: 'Value'})).toHaveValue('{"a":1}');

		await userEvent.click(dialog.getByRole('button', {name: 'Open JSON editor'}));
		await expect.element(await waitForEditor(dialog, 'Value')).toHaveFocus();
		await settleEditor();
		await userEvent.click(dialog.getByRole('button', {name: 'Save'}));

		await expect.element(dialog.getByRole('textbox', {name: 'Value'})).toHaveValue('{\t"a": 1}');
	});
});
