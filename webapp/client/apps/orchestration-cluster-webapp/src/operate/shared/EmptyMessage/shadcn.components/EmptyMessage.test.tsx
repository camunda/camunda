/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {EmptyMessage} from './EmptyMessage';

describe('<EmptyMessage />', () => {
	it('should render the message', async () => {
		const screen = await render(<EmptyMessage message="No data available" />);

		await expect.element(screen.getByText('No data available')).toBeVisible();
	});

	it('should render the additional info when provided', async () => {
		const screen = await render(<EmptyMessage message="No data available" additionalInfo="Try again later" />);

		await expect.element(screen.getByText('Try again later')).toBeVisible();
	});

	it('should not render additional info when not provided', async () => {
		const screen = await render(<EmptyMessage message="No data available" />);

		expect(screen.getByText('Try again later').elements()).toHaveLength(0);
	});
});
