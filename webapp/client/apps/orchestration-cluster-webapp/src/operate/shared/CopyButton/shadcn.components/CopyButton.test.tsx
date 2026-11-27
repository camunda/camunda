/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TooltipProvider} from '@camunda/design-system';
import {render} from 'vitest-browser-react';
import {describe, expect, vi, beforeEach, afterEach} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {CopyButton} from './CopyButton';

function renderWithTooltipProvider(children: React.ReactElement) {
	return render(children, {wrapper: TooltipProvider});
}

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
		const screen = await renderWithTooltipProvider(<CopyButton value="some-value" />);

		await expect.element(screen.getByRole('button', {name: 'Copy', exact: true})).toBeVisible();
	});

	it('should copy the value to clipboard when clicked', async () => {
		const screen = await renderWithTooltipProvider(<CopyButton value="hello world" />);

		await userEvent.click(screen.getByRole('button', {name: 'Copy', exact: true}));

		expect(mockWriteText).toHaveBeenCalledWith('hello world');
	});

	it('should transform the value only when clicked', async () => {
		const transformValue = vi.fn((value: string) => `transformed ${value}`);
		const screen = await renderWithTooltipProvider(<CopyButton value="hello world" transformValue={transformValue} />);

		expect(transformValue).not.toHaveBeenCalled();

		await userEvent.click(screen.getByRole('button', {name: 'Copy', exact: true}));

		expect(transformValue).toHaveBeenCalledOnce();
		expect(mockWriteText).toHaveBeenCalledWith('transformed hello world');
		await expect.element(screen.getByRole('button', {name: 'Copied'})).toBeVisible();
	});

	it('should show copied feedback after clicking', async () => {
		const screen = await renderWithTooltipProvider(<CopyButton value="hello world" />);

		await userEvent.click(screen.getByRole('button', {name: 'Copy', exact: true}));

		await expect.element(screen.getByRole('button', {name: 'Copied'})).toBeVisible();
	});

	it('should reset to copy state after the feedback timeout', async () => {
		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout']});

		const screen = await renderWithTooltipProvider(<CopyButton value="hello world" />);

		await userEvent.click(screen.getByRole('button', {name: 'Copy', exact: true}));
		await expect.element(screen.getByText('Copied')).toBeVisible();

		await vi.advanceTimersByTimeAsync(5000);

		await expect.element(screen.getByText('Copy', {exact: true})).toBeVisible();
	});

	it('should reset copied state when value prop changes', async () => {
		const screen = await renderWithTooltipProvider(<CopyButton value="first" />);

		await userEvent.click(screen.getByRole('button', {name: 'Copy', exact: true}));
		await expect.element(screen.getByText('Copied')).toBeVisible();

		await screen.rerender(<CopyButton value="second" />);

		await expect.element(screen.getByText('Copy', {exact: true})).toBeVisible();
	});

	it('should not re-enter the copied state when the value cycles back without a new copy action', async () => {
		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout']});
		const screen = await renderWithTooltipProvider(<CopyButton value="first" />);
		await userEvent.click(screen.getByRole('button', {name: 'Copy', exact: true}));
		await expect.element(screen.getByRole('button', {name: 'Copied'})).toBeVisible();
		await screen.rerender(<CopyButton value="second" />);
		await expect.element(screen.getByRole('button', {name: 'Copy', exact: true})).toBeVisible();

		await screen.rerender(<CopyButton value="first" />);
		vi.clearAllTimers();

		await expect.element(screen.getByRole('button', {name: 'Copy', exact: true})).toBeVisible();
	});

	it('should not show copied feedback for a write that resolves after the value cycled back', async () => {
		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout']});
		const pendingWrite = {resolve: () => {}};
		mockWriteText.mockImplementation(
			() =>
				new Promise<void>((resolve) => {
					pendingWrite.resolve = resolve;
				}),
		);
		const screen = await renderWithTooltipProvider(<CopyButton value="first" />);
		await userEvent.click(screen.getByRole('button', {name: 'Copy', exact: true}));
		await screen.rerender(<CopyButton value="second" />);
		await screen.rerender(<CopyButton value="first" />);

		pendingWrite.resolve();
		await screen.rerender(<CopyButton value="first" />);
		vi.clearAllTimers();

		await expect.element(screen.getByRole('button', {name: 'Copy', exact: true})).toBeVisible();
	});

	it('should stay in the copy state when the clipboard write is rejected', async () => {
		mockWriteText.mockRejectedValue(new Error('permission denied'));

		const screen = await renderWithTooltipProvider(<CopyButton value="hello world" />);

		await userEvent.click(screen.getByRole('button', {name: 'Copy', exact: true}));

		await expect.element(screen.getByText('Copy', {exact: true})).toBeVisible();
	});

	it('should not throw when the clipboard API is unavailable', async () => {
		Object.defineProperty(navigator, 'clipboard', {
			value: undefined,
			configurable: true,
			writable: true,
		});

		const screen = await renderWithTooltipProvider(<CopyButton value="hello world" />);

		await userEvent.click(screen.getByRole('button', {name: 'Copy', exact: true}));

		await expect.element(screen.getByText('Copy', {exact: true})).toBeVisible();
	});

	it('should use the tooltip label for the icon-only variant', async () => {
		const screen = await renderWithTooltipProvider(
			<CopyButton value="hello world" hasIconOnly tooltipAlignment="top-end" />,
		);

		await expect.element(screen.getByRole('button', {name: 'Copy to clipboard'})).toBeVisible();

		await userEvent.hover(screen.getByRole('button', {name: 'Copy to clipboard'}));

		await expect.element(screen.getByRole('tooltip')).toMatchTextContent('Copy to clipboard');
	});

	it('should update the icon-only button label after copying', async () => {
		const screen = await renderWithTooltipProvider(<CopyButton value="hello world" hasIconOnly />);

		await userEvent.click(screen.getByRole('button', {name: 'Copy to clipboard'}));

		await expect.element(screen.getByRole('button', {name: 'Copied to clipboard'})).toBeVisible();
	});
});
