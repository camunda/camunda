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
import {DrainingTag} from './DrainingTag';

describe('<DrainingTag />', () => {
	it('should render the label', async () => {
		const screen = await render(<DrainingTag label="Draining" description="This version is draining" />);

		await expect.element(screen.getByTestId('draining-tag')).toHaveTextContent('Draining');
	});

	it('should show the description in a tooltip on hover', async () => {
		const screen = await render(<DrainingTag label="Draining" description="This version is draining" />);

		await userEvent.hover(screen.getByTestId('draining-tag'));

		await expect.element(screen.getByText('This version is draining')).toBeVisible();
	});

	it('should apply a custom className to the tag', async () => {
		const screen = await render(
			<DrainingTag label="Draining" description="This version is draining" className="custom-class" />,
		);

		await expect.element(screen.getByTestId('draining-tag')).toHaveClass('custom-class');
	});
});
