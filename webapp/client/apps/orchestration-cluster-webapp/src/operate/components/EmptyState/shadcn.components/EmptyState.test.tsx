/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {EmptyState} from './EmptyState';

describe('<EmptyState />', () => {
	it('should render the button as a link when it has an href', async () => {
		const screen = await render(
			<EmptyState heading="Heading" description="Description" icon={<svg />} button={{label: 'Go', href: '/go'}} />,
		);

		await expect.element(screen.getByRole('link', {name: 'Go'})).toHaveAttribute('href', '/go');
	});

	it('should render the button as a button when it has no href', async () => {
		const onClick = vi.fn();
		const screen = await render(
			<EmptyState heading="Heading" description="Description" icon={<svg />} button={{label: 'Go', onClick}} />,
		);

		await userEvent.click(screen.getByRole('button', {name: 'Go'}));

		expect(onClick).toHaveBeenCalledTimes(1);
	});

	it('should render the secondary link', async () => {
		const screen = await render(
			<EmptyState
				heading="Heading"
				description="Description"
				icon={<svg />}
				link={{label: 'Learn more', href: '/learn-more'}}
			/>,
		);

		await expect.element(screen.getByRole('link', {name: 'Learn more'})).toHaveAttribute('href', '/learn-more');
	});
});
