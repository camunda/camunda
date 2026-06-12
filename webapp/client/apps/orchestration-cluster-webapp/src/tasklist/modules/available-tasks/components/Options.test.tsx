/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect} from 'vitest';
import {Options} from './Options';

describe('<Options />', () => {
	it('should render the auto-select toggle defaulting to off', async () => {
		const screen = await render(<Options />);

		const toggle = screen.getByRole('switch');
		await expect.element(toggle).toBeVisible();
		await expect.element(toggle).not.toBeChecked();
	});

	it('should have an accessible section label', async () => {
		const screen = await render(<Options />);

		await expect.element(screen.getByRole('region', {name: 'Options'})).toBeVisible();
	});
});
