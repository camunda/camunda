/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {cleanup, render} from 'vitest-browser-react';
import {afterEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {HelpMenu} from './HelpMenu';

describe('Info bar', () => {
	afterEach(async () => {
		await cleanup();
		vi.unstubAllGlobals();
	});

	it('should render with correct links', async () => {
		const mockOpenFn = vi.fn();
		vi.stubGlobal('open', mockOpenFn);
		const screen = await render(<HelpMenu isPaidPlan={false} />);

		await userEvent.click(screen.getByRole('button', {name: 'Info'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Documentation'}));
		expect(mockOpenFn).toHaveBeenLastCalledWith('https://docs.camunda.io/', '_blank', 'noopener,noreferrer');

		await userEvent.click(screen.getByRole('button', {name: 'Info'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Camunda Academy'}));
		expect(mockOpenFn).toHaveBeenLastCalledWith('https://academy.camunda.com/', '_blank', 'noopener,noreferrer');

		await userEvent.click(screen.getByRole('button', {name: 'Info'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Community Forum'}));
		expect(mockOpenFn).toHaveBeenLastCalledWith('https://forum.camunda.io', '_blank', 'noopener,noreferrer');
	});

	it('should not render feedback and support link for free plan', async () => {
		const screen = await render(<HelpMenu isPaidPlan={false} />);

		await userEvent.click(screen.getByRole('button', {name: 'Info'}));

		expect(screen.getByRole('menuitem', {name: 'Feedback and Support'}).elements()).toHaveLength(0);
	});

	it.for(['enterprise', 'paid-cc'])('should render correct links for feedback and support - %s', async () => {
		const mockOpenFn = vi.fn();
		vi.stubGlobal('open', mockOpenFn);
		const screen = await render(<HelpMenu isPaidPlan />);

		await userEvent.click(screen.getByRole('button', {name: 'Info'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Feedback and Support'}));

		expect(mockOpenFn).toHaveBeenLastCalledWith(
			'https://jira.camunda.com/projects/SUPPORT/queues',
			'_blank',
			'noopener,noreferrer',
		);
	});
});
