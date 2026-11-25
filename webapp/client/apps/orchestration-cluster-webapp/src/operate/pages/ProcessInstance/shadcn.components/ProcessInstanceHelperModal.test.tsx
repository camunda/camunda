/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {ProcessInstanceHelperModal} from './ProcessInstanceHelperModal';

const TITLE = "Here's what moved in Operate";

describe('<ProcessInstanceHelperModal />', () => {
	afterEach(() => {
		localStorage.clear();
	});

	it('should show what moved until it is dismissed', async () => {
		const screen = await render(<ProcessInstanceHelperModal />);

		const helper = screen.getByRole('dialog', {name: TITLE});
		await expect.element(helper).toBeVisible();
		await expect
			.element(helper.getByRole('img', {name: 'Process instance details page with incidents tab and diagram'}))
			.toBeVisible();
		await expect.element(helper.getByRole('button', {name: 'Got it'})).toHaveFocus();
		await userEvent.click(helper.getByRole('button', {name: 'Got it'}));
		await expect.element(helper).not.toBeInTheDocument();

		await screen.unmount();
		const nextVisit = await render(<ProcessInstanceHelperModal />);

		await expect.element(nextVisit.getByRole('dialog', {name: TITLE})).not.toBeInTheDocument();
	});

	it('should stay dismissed after closing it with Escape', async () => {
		const screen = await render(<ProcessInstanceHelperModal />);

		await expect.element(screen.getByRole('dialog', {name: TITLE})).toBeVisible();
		await userEvent.keyboard('{Escape}');
		await expect.element(screen.getByRole('dialog', {name: TITLE})).not.toBeInTheDocument();

		await screen.unmount();
		const nextVisit = await render(<ProcessInstanceHelperModal />);

		await expect.element(nextVisit.getByRole('dialog', {name: TITLE})).not.toBeInTheDocument();
	});

	it('should not show what moved when it was dismissed in legacy Operate', async () => {
		localStorage.setItem('sharedState', JSON.stringify({hideProcessInstanceHelperModal: true}));

		const screen = await render(<ProcessInstanceHelperModal />);

		await expect.element(screen.getByRole('dialog', {name: TITLE})).not.toBeInTheDocument();
	});
});
