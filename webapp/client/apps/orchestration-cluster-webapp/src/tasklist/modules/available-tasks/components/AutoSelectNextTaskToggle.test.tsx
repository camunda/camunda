/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {beforeEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {AutoSelectNextTaskToggle} from './AutoSelectNextTaskToggle';

const SWITCH_NAME = 'Auto-select first available task';

describe('<AutoSelectNextTaskToggle />', () => {
	beforeEach(() => {
		localStorage.clear();
	});

	it('should render the auto-select toggle defaulting to off', async () => {
		const screen = await render(<AutoSelectNextTaskToggle />);

		const toggle = screen.getByRole('switch', {name: SWITCH_NAME});
		await expect.element(toggle).toBeVisible();
		await expect.element(toggle).not.toBeChecked();
	});

	it('should have an accessible section label', async () => {
		const screen = await render(<AutoSelectNextTaskToggle />);

		await expect.element(screen.getByRole('region', {name: 'Options'})).toBeVisible();
	});

	it('should render the auto-select toggle enabled when stored locally', async () => {
		storeStateLocally('tasklist.autoSelectNextTask', true);

		const screen = await render(<AutoSelectNextTaskToggle />);

		await expect.element(screen.getByRole('switch', {name: SWITCH_NAME})).toBeChecked();
	});

	it('should enable auto-select and store the preference locally', async () => {
		const screen = await render(<AutoSelectNextTaskToggle />);
		const toggle = screen.getByRole('switch', {name: SWITCH_NAME});

		await userEvent.click(toggle, {force: true});

		await expect.element(toggle).toBeChecked();
		expect(getStateLocally('tasklist.autoSelectNextTask')).toBe(true);
	});

	it('should disable auto-select and store the preference locally', async () => {
		storeStateLocally('tasklist.autoSelectNextTask', true);
		const screen = await render(<AutoSelectNextTaskToggle />);
		const toggle = screen.getByRole('switch', {name: SWITCH_NAME});

		await expect.element(toggle).toBeChecked();

		await userEvent.click(toggle, {force: true});

		await expect.element(toggle).not.toBeChecked();
		expect(getStateLocally('tasklist.autoSelectNextTask')).toBe(false);

		await userEvent.click(toggle, {force: true});

		await expect.element(toggle).toBeChecked();
		expect(getStateLocally('tasklist.autoSelectNextTask')).toBe(true);
	});
});
