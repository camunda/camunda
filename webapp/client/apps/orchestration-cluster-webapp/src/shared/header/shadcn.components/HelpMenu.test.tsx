/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {HelpMenu} from './HelpMenu';

describe('Info bar', () => {
	it.for([
		['Documentation', 'https://docs.camunda.io/'],
		['Camunda Academy', 'https://academy.camunda.com/'],
		['Community Forum', 'https://forum.camunda.io'],
	] as const)('should render the %s link', async ([name, href]) => {
		const screen = await render(<HelpMenu isPaidPlan={false} />);

		await userEvent.click(screen.getByRole('button', {name: 'Info'}));

		const link = screen.getByRole('menuitem', {name});
		await expect.element(link).toHaveAttribute('href', href);
		await expect.element(link).toHaveAttribute('target', '_blank');
		await expect.element(link).toHaveAttribute('rel', 'noopener noreferrer');
	});

	it('should not render feedback and support link for free plan', async () => {
		const screen = await render(<HelpMenu isPaidPlan={false} />);

		await userEvent.click(screen.getByRole('button', {name: 'Info'}));

		expect(screen.getByRole('menuitem', {name: 'Feedback and Support'}).elements()).toHaveLength(0);
	});

	it('should render the feedback and support link for paid plans', async () => {
		const screen = await render(<HelpMenu isPaidPlan />);

		await userEvent.click(screen.getByRole('button', {name: 'Info'}));

		const link = screen.getByRole('menuitem', {name: 'Feedback and Support'});
		await expect.element(link).toHaveAttribute('href', 'https://jira.camunda.com/projects/SUPPORT/queues');
		await expect.element(link).toHaveAttribute('target', '_blank');
	});
});
