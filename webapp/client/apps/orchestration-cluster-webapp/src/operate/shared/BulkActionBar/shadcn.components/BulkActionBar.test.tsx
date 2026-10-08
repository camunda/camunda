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
import {BulkActionBar} from './BulkActionBar';
import {BulkActionConfirmDialog} from './BulkActionConfirmDialog';

describe('<BulkActionBar />', () => {
	it('should render the selection label and trigger actions and discard', async () => {
		const onDelete = vi.fn();
		const onDiscard = vi.fn();
		const screen = await render(
			<BulkActionBar
				selectedLabel="2 items selected"
				discardLabel="Discard"
				actions={[{key: 'delete', label: 'Delete', onClick: onDelete}]}
				onDiscard={onDiscard}
			/>,
		);

		await expect.element(screen.getByRole('status')).toHaveTextContent('2 items selected');
		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));
		await userEvent.click(screen.getByRole('button', {name: 'Discard'}));

		expect(onDelete).toHaveBeenCalledOnce();
		expect(onDiscard).toHaveBeenCalledOnce();
	});

	it('should keep a disabled action focusable and expose its reason as title', async () => {
		const onRetry = vi.fn();
		const screen = await render(
			<BulkActionBar
				selectedLabel="1 item selected"
				discardLabel="Discard"
				actions={[{key: 'retry', label: 'Retry', disabled: true, title: 'No incidents selected', onClick: onRetry}]}
				onDiscard={vi.fn()}
			/>,
		);

		const retry = screen.getByRole('button', {name: 'Retry'});

		await expect.element(retry).toHaveAttribute('aria-disabled', 'true');
		await expect.element(retry).toHaveAttribute('title', 'No incidents selected');
		await userEvent.tab();
		await expect.element(retry).toHaveFocus();
		await userEvent.keyboard('{Enter}');
		expect(onRetry).not.toHaveBeenCalled();
	});

	it('should render additional actions before the standard actions', async () => {
		const screen = await render(
			<BulkActionBar
				selectedLabel="1 item selected"
				discardLabel="Discard"
				actions={[]}
				additionalActions={<button type="button">Move</button>}
				onDiscard={vi.fn()}
			/>,
		);

		await expect.element(screen.getByRole('button', {name: 'Move'})).toBeVisible();
	});
});

describe('<BulkActionConfirmDialog />', () => {
	it('should confirm and cancel', async () => {
		const onConfirm = vi.fn();
		const onCancel = vi.fn();
		const screen = await render(
			<BulkActionConfirmDialog
				open
				title="Apply operation"
				description="Are you sure?"
				confirmLabel="Apply"
				cancelLabel="Cancel"
				onOpenChange={vi.fn()}
				onCancel={onCancel}
				onConfirm={onConfirm}
			/>,
		);

		await expect.element(screen.getByText('Are you sure?')).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Apply'}));
		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}));

		expect(onConfirm).toHaveBeenCalledOnce();
		expect(onCancel).toHaveBeenCalledOnce();
	});

	it('should disable the confirm button when requested', async () => {
		const screen = await render(
			<BulkActionConfirmDialog
				open
				title="Apply operation"
				description="Not allowed"
				confirmLabel="Apply"
				cancelLabel="Cancel"
				isConfirmDisabled
				onOpenChange={vi.fn()}
				onCancel={vi.fn()}
				onConfirm={vi.fn()}
			/>,
		);

		await expect.element(screen.getByRole('button', {name: 'Apply'})).toBeDisabled();
	});
});
