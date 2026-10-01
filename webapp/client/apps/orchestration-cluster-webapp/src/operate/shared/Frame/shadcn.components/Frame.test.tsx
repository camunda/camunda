/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, it, expect} from 'vitest';
import {Frame} from './Frame';

describe('<Frame />', () => {
	it('should render only children when no frame is given', async () => {
		const screen = await render(<Frame>content</Frame>);

		await expect.element(screen.getByText('content')).toBeVisible();
		expect(screen.getByTestId('frame-container').elements()).toHaveLength(0);
	});

	it('should render the header title when the frame is visible', async () => {
		const screen = await render(
			<Frame frame={{headerTitle: 'My Frame'}}>
				<div>content</div>
			</Frame>,
		);

		await expect.element(screen.getByTestId('frame-container')).toBeVisible();
		await expect.element(screen.getByText('My Frame')).toBeVisible();
		await expect.element(screen.getByText('content')).toBeVisible();
	});

	it('should hide the header title when isVisible is false', async () => {
		const screen = await render(
			<Frame frame={{headerTitle: 'My Frame', isVisible: false}}>
				<div>content</div>
			</Frame>,
		);

		await expect.element(screen.getByTestId('frame-container')).toBeVisible();
		expect(screen.getByText('My Frame').elements()).toHaveLength(0);
		await expect.element(screen.getByText('content')).toBeVisible();
	});
});
