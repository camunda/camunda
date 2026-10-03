/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, it, expect} from 'vitest';
import {ErrorMessage} from './ErrorMessage';

describe('<ErrorMessage />', () => {
	it('should render the default error message', async () => {
		const screen = await render(<ErrorMessage />);

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
		await expect.element(screen.getByText('Refresh the page to try again')).toBeVisible();
	});

	it('should render a custom message when provided', async () => {
		const screen = await render(<ErrorMessage message="Custom error" additionalInfo="Custom info" />);

		await expect.element(screen.getByText('Custom error')).toBeVisible();
		await expect.element(screen.getByText('Custom info')).toBeVisible();
	});
});
