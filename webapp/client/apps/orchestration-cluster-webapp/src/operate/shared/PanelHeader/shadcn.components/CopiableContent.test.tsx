/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {CopiableContent} from './CopiableContent';

describe('<CopiableContent />', () => {
	afterEach(() => {
		vi.restoreAllMocks();
		vi.useRealTimers();
	});

	it('should render the content as a copy button', async () => {
		const screen = await render(<CopiableContent content="invoice-classification" copyButtonDescription="Copy ID" />);

		await expect.element(screen.getByRole('button', {name: 'Copy ID: invoice-classification'})).toBeVisible();
		await expect.element(screen.getByText('invoice-classification')).toBeVisible();
	});

	it('should copy the content and announce feedback', async () => {
		const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue(undefined);

		const screen = await render(<CopiableContent content="invoice-classification" copyButtonDescription="Copy ID" />);
		await userEvent.click(screen.getByRole('button', {name: 'Copy ID: invoice-classification'}));

		expect(writeText).toHaveBeenCalledWith('invoice-classification');
		await expect.element(screen.getByRole('status')).toHaveTextContent('Copied to clipboard');
	});

	it('should fall back to the copy command when the clipboard API is unavailable', async () => {
		vi.spyOn(navigator, 'clipboard', 'get').mockReturnValue(undefined as unknown as Clipboard);
		const execCommand = vi.spyOn(document, 'execCommand').mockReturnValue(true);
		const screen = await render(<CopiableContent content="invoice-classification" copyButtonDescription="Copy ID" />);

		await userEvent.click(screen.getByRole('button', {name: 'Copy ID: invoice-classification'}));

		expect(execCommand).toHaveBeenCalledWith('copy');
		await expect.element(screen.getByRole('status')).toHaveTextContent('Copied to clipboard');
	});

	it('should not show feedback when the copy command is rejected', async () => {
		vi.spyOn(navigator, 'clipboard', 'get').mockReturnValue(undefined as unknown as Clipboard);
		vi.spyOn(document, 'execCommand').mockReturnValue(false);
		const screen = await render(<CopiableContent content="invoice-classification" copyButtonDescription="Copy ID" />);

		await userEvent.click(screen.getByRole('button', {name: 'Copy ID: invoice-classification'}));

		await expect.element(screen.getByRole('status')).toHaveTextContent('');
	});

	it('should clear the feedback after the timeout', async () => {
		vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue(undefined);

		const screen = await render(<CopiableContent content="invoice-classification" copyButtonDescription="Copy ID" />);
		await userEvent.click(screen.getByRole('button', {name: 'Copy ID: invoice-classification'}));
		await expect.element(screen.getByRole('status')).toHaveTextContent('Copied to clipboard');

		await expect.element(screen.getByRole('status'), {timeout: 4000}).toBeEmptyDOMElement();
	});

	it('should not show feedback and should notify when copying fails', async () => {
		vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValue(new Error('denied'));
		notificationsStore.reset();

		const screen = await render(<CopiableContent content="invoice-classification" copyButtonDescription="Copy ID" />);
		await userEvent.click(screen.getByRole('button', {name: 'Copy ID: invoice-classification'}));

		await expect.element(screen.getByRole('status')).toBeEmptyDOMElement();
		expect(notificationsStore.notifications).toEqual([
			expect.objectContaining({kind: 'error', title: "Couldn't copy to clipboard"}),
		]);
	});

	it('should not keep the feedback when the content changes', async () => {
		vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue(undefined);

		const screen = await render(<CopiableContent content="invoice-classification" copyButtonDescription="Copy ID" />);
		await userEvent.click(screen.getByRole('button', {name: 'Copy ID: invoice-classification'}));
		await expect.element(screen.getByRole('status')).toHaveTextContent('Copied to clipboard');

		await screen.rerender(<CopiableContent content="order-approval" copyButtonDescription="Copy ID" />);

		await expect.element(screen.getByRole('status')).toBeEmptyDOMElement();
	});

	it('should not bring back the feedback when the content returns to the copied value', async () => {
		vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue(undefined);

		const screen = await render(<CopiableContent content="invoice-classification" copyButtonDescription="Copy ID" />);
		await userEvent.click(screen.getByRole('button', {name: 'Copy ID: invoice-classification'}));
		await expect.element(screen.getByRole('status')).toHaveTextContent('Copied to clipboard');

		await screen.rerender(<CopiableContent content="order-approval" copyButtonDescription="Copy ID" />);
		await screen.rerender(<CopiableContent content="invoice-classification" copyButtonDescription="Copy ID" />);

		await expect.element(screen.getByRole('status')).toBeEmptyDOMElement();
	});

	it('should ignore a pending copy that resolves after the content changed and returned', async () => {
		let resolveWrite: () => void = () => {};
		vi.spyOn(navigator.clipboard, 'writeText').mockReturnValue(
			new Promise<void>((resolve) => {
				resolveWrite = resolve;
			}),
		);

		const screen = await render(<CopiableContent content="invoice-classification" copyButtonDescription="Copy ID" />);
		await userEvent.click(screen.getByRole('button', {name: 'Copy ID: invoice-classification'}));
		await screen.rerender(<CopiableContent content="order-approval" copyButtonDescription="Copy ID" />);
		await screen.rerender(<CopiableContent content="invoice-classification" copyButtonDescription="Copy ID" />);
		resolveWrite();

		await expect.element(screen.getByRole('status')).toBeEmptyDOMElement();
	});
});
