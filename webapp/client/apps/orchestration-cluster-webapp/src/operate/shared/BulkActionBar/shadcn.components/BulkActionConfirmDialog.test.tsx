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
import {BulkActionConfirmDialog} from './BulkActionConfirmDialog';

async function renderDialog(props: Partial<React.ComponentProps<typeof BulkActionConfirmDialog>> = {}) {
	const onConfirm = vi.fn();
	const onCancel = vi.fn();
	const onOpenChange = vi.fn();
	const screen = await render(
		<BulkActionConfirmDialog
			open
			title="Delete decisions"
			description="This cannot be undone."
			confirmLabel="Delete"
			cancelLabel="Cancel"
			onOpenChange={onOpenChange}
			onCancel={onCancel}
			onConfirm={onConfirm}
			{...props}
		/>,
	);
	return {screen, onConfirm, onCancel, onOpenChange};
}

describe('<BulkActionConfirmDialog /> (design system)', () => {
	it('should render the title, description and both actions when open', async () => {
		const {screen} = await renderDialog();

		await expect.element(screen.getByRole('alertdialog', {name: 'Delete decisions'})).toBeVisible();
		await expect.element(screen.getByText('This cannot be undone.')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Delete'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Cancel'})).toBeVisible();
	});

	it('should render nothing when closed', async () => {
		const {screen} = await renderDialog({open: false});

		await expect.element(screen.getByRole('alertdialog')).not.toBeInTheDocument();
	});

	it('should call onConfirm when the confirm action is clicked', async () => {
		const {screen, onConfirm} = await renderDialog();

		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));

		expect(onConfirm).toHaveBeenCalledOnce();
	});

	it('should call onCancel when the cancel action is clicked', async () => {
		const {screen, onCancel, onConfirm} = await renderDialog();

		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}));

		expect(onCancel).toHaveBeenCalledOnce();
		expect(onConfirm).not.toHaveBeenCalled();
	});

	it('should disable the confirm action when confirming is disabled', async () => {
		const {screen} = await renderDialog({isConfirmDisabled: true});

		await expect.element(screen.getByRole('button', {name: 'Delete'})).toBeDisabled();
	});

	it('should leave closing the dialog to the parent after confirming', async () => {
		const {screen, onOpenChange} = await renderDialog();

		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));

		expect(onOpenChange).not.toHaveBeenCalledWith(false);
	});
});
