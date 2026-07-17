/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, it, expect, vi, beforeEach, afterEach} from 'vitest';
import {userEvent} from 'vitest/browser';
import {CopyButton} from './CopyButton';

describe('<CopyButton />', () => {
	const mockWriteText = vi.fn();

	beforeEach(() => {
		mockWriteText.mockResolvedValue(undefined);
		Object.defineProperty(navigator, 'clipboard', {
			value: {writeText: mockWriteText},
			configurable: true,
			writable: true,
		});
	});

	afterEach(() => {
		vi.useRealTimers();
		vi.clearAllMocks();
		Object.defineProperty(navigator, 'clipboard', {
			value: undefined,
			configurable: true,
			writable: true,
		});
	});

	it('should render the copy button with default label and icon', async () => {
		const screen = await render(<CopyButton value="some-value" />);

		await expect.element(screen.getByRole('button', {name: 'Copy'})).toBeVisible();
	});

	it('should copy the value to clipboard when clicked', async () => {
		const screen = await render(<CopyButton value="hello world" />);

		await userEvent.click(screen.getByRole('button', {name: 'Copy'}).element());

		expect(mockWriteText).toHaveBeenCalledWith('hello world');
	});

	it('should show copied feedback after clicking', async () => {
		const screen = await render(<CopyButton value="hello world" />);

		await userEvent.click(screen.getByRole('button', {name: 'Copy'}).element());

		await expect.element(screen.getByRole('button', {name: 'Copied'})).toBeVisible();
	});

	it('should reset to copy state after the feedback timeout', async () => {
		vi.useFakeTimers({shouldAdvanceTime: true});

		const screen = await render(<CopyButton value="hello world" />);

		await userEvent.click(screen.getByRole('button', {name: 'Copy'}).element());
		await expect.element(screen.getByText('Copied')).toBeVisible();

		await vi.advanceTimersByTimeAsync(5000);

		await expect.element(screen.getByText('Copy')).toBeVisible();
	});

	it('should reset copied state when value prop changes', async () => {
		const screen = await render(<CopyButton value="first" />);

		await userEvent.click(screen.getByRole('button', {name: 'Copy'}).element());
		await expect.element(screen.getByText('Copied')).toBeVisible();

		await screen.rerender(<CopyButton value="second" />);

		await expect.element(screen.getByText('Copy')).toBeVisible();
	});

	it('should cancel the feedback timeout when the value changes mid-countdown', async () => {
		const screen = await render(<CopyButton value="first" />);

		await userEvent.click(screen.getByRole('button', {name: 'Copy'}).element());
		await expect.element(screen.getByText('Copied')).toBeVisible();

		vi.useFakeTimers({shouldAdvanceTime: true});

		await screen.rerender(<CopyButton value="second" />);
		await expect.element(screen.getByText('Copy')).toBeVisible();

		await vi.advanceTimersByTimeAsync(5000);
		await expect.element(screen.getByText('Copy')).toBeVisible();
	});
});
