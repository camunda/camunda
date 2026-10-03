/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {DangerButton} from './DangerButton';

describe('<DangerButton />', () => {
	it('should render the destructive delete button with translated text', async () => {
		const screen = await render(<DangerButton type="DELETE" onClick={vi.fn()} title="Delete Instance 123" />);

		const button = screen.getByRole('button', {name: 'Delete Instance 123', exact: true});

		await expect.element(button).toBeVisible();
		await expect.element(button).toHaveAttribute('title', 'Delete Instance 123');
		await expect.element(button).toHaveAttribute('data-size', 'default');
		await expect.element(screen.getByText('Delete', {exact: true})).toBeVisible();
		await expect.element(screen.getByTitle('Delete Instance 123')).toBeVisible();
		await expect.element(screen.getByTestId('delete-operation')).toBeVisible();
	});

	it.for([
		['xs', 'xs'],
		['sm', 'sm'],
		['md', 'default'],
		['lg', 'lg'],
		['xl', 'lg'],
		['2xl', 'lg'],
	] as const)('should map the %s size to %s', async ([size, mappedSize]) => {
		const screen = await render(
			<DangerButton type="DELETE" onClick={vi.fn()} title="Delete Instance 123" size={size} />,
		);

		await expect
			.element(screen.getByRole('button', {name: 'Delete Instance 123', exact: true}))
			.toHaveAttribute('data-size', mappedSize);
	});

	it('should execute the delete action when clicked', async () => {
		const onClick = vi.fn();
		const screen = await render(<DangerButton type="DELETE" onClick={onClick} title="Delete Instance 123" />);

		await userEvent.click(screen.getByRole('button', {name: 'Delete Instance 123', exact: true}));

		expect(onClick).toHaveBeenCalledOnce();
	});

	it('should preserve the disabled state', async () => {
		const screen = await render(<DangerButton type="DELETE" onClick={vi.fn()} title="Delete Instance 123" disabled />);

		const button = screen.getByRole('button', {name: 'Delete Instance 123', exact: true});

		await expect.element(button).toBeDisabled();
	});
});
