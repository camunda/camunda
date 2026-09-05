/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {beforeEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {FirstTimeProcessWarning} from './FirstTimeProcessWarning';

describe('<FirstTimeProcessWarning />', () => {
	beforeEach(() => {
		localStorage.clear();
	});

	it('should show the warning and gate its children before consent', async () => {
		const screen = await renderWithRouter(
			() => (
				<FirstTimeProcessWarning>
					<div>Start form</div>
				</FirstTimeProcessWarning>
			),
			{path: '/shadcn/tasklist/processes'},
		);

		await expect.element(screen.getByRole('dialog', {name: 'Start your process on demand'})).toBeVisible();
		await expect.element(screen.getByText('Start form')).not.toBeInTheDocument();
	});

	it('should store consent and render its children after continuing', async () => {
		const screen = await renderWithRouter(
			() => (
				<FirstTimeProcessWarning>
					<div>Start form</div>
				</FirstTimeProcessWarning>
			),
			{path: '/shadcn/tasklist/processes'},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Continue'}));

		expect(getStateLocally('tasklist.hasConsentedToStartProcess')).toBe(true);
		await expect.element(screen.getByText('Start form')).toBeVisible();
		await expect.element(screen.getByRole('dialog', {name: 'Start your process on demand'})).not.toBeInTheDocument();
	});

	it('should render its children immediately when consent already exists', async () => {
		storeStateLocally('tasklist.hasConsentedToStartProcess', true);

		const screen = await renderWithRouter(
			() => (
				<FirstTimeProcessWarning>
					<div>Start form</div>
				</FirstTimeProcessWarning>
			),
			{path: '/shadcn/tasklist/processes'},
		);

		await expect.element(screen.getByText('Start form')).toBeVisible();
		await expect.element(screen.getByRole('dialog', {name: 'Start your process on demand'})).not.toBeInTheDocument();
	});

	it('should navigate to the task list when cancelled', async () => {
		const {router, ...screen} = await renderWithRouter(() => <FirstTimeProcessWarning />, {
			path: '/shadcn/tasklist/processes',
		});

		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}));

		await expect.poll(() => router.state.location.pathname).toBe('/shadcn/tasklist');
		expect(getStateLocally('tasklist.hasConsentedToStartProcess')).toBeNull();
	});

	it('should navigate to the task list when closed', async () => {
		const {router, ...screen} = await renderWithRouter(() => <FirstTimeProcessWarning />, {
			path: '/shadcn/tasklist/processes',
		});

		await userEvent.click(screen.getByRole('button', {name: 'Close'}));

		await expect.poll(() => router.state.location.pathname).toBe('/shadcn/tasklist');
		expect(getStateLocally('tasklist.hasConsentedToStartProcess')).toBeNull();
	});
});
