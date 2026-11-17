/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {DeleteDefinitionModal} from './DeleteDefinitionModal';

describe('<DeleteDefinitionModal />', () => {
	const ControlledDeleteDefinitionModal = ({
		onClose = () => {},
		onDelete = () => {},
	}: {
		onClose?: () => void;
		onDelete?: () => void;
	}) => {
		const [isVisible, setIsVisible] = useState(true);

		return (
			<>
				<button type="button" onClick={() => setIsVisible(true)}>
					reopen
				</button>
				<DeleteDefinitionModal
					isVisible={isVisible}
					title="warning"
					description="description"
					bodyContent={<div>body</div>}
					confirmationText="confirm"
					onClose={() => {
						onClose();
						setIsVisible(false);
					}}
					onDelete={() => {
						onDelete();
						setIsVisible(false);
					}}
				/>
			</>
		);
	};

	it('starts with an unchecked confirmation checkbox', async () => {
		const screen = await render(
			<DeleteDefinitionModal
				isVisible
				title="warning"
				description="description"
				bodyContent={<div>body</div>}
				confirmationText="confirm"
				onClose={() => {}}
				onDelete={() => {}}
			/>,
		);

		await expect.element(screen.getByRole('checkbox', {name: 'confirm'})).not.toBeChecked();
	});

	it('disables the delete button until the checkbox is confirmed', async () => {
		const onDelete = vi.fn();
		const screen = await render(
			<DeleteDefinitionModal
				isVisible
				title="warning"
				description="description"
				bodyContent={<div>body</div>}
				confirmationText="confirm"
				onClose={() => {}}
				onDelete={onDelete}
			/>,
		);

		await expect.element(screen.getByRole('button', {name: 'Delete'})).toBeDisabled();
		expect(onDelete).not.toHaveBeenCalled();
	});

	it('deletes once the checkbox is confirmed', async () => {
		const onDelete = vi.fn();
		const screen = await render(
			<DeleteDefinitionModal
				isVisible
				title="warning"
				description="description"
				bodyContent={<div>body</div>}
				confirmationText="confirm"
				onClose={() => {}}
				onDelete={onDelete}
			/>,
		);

		await userEvent.click(screen.getByText('confirm'));
		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));

		expect(onDelete).toHaveBeenCalledTimes(1);
	});

	it('stays open when clicking outside the dialog', async () => {
		const onClose = vi.fn();
		const screen = await render(<ControlledDeleteDefinitionModal onClose={onClose} />);

		await userEvent.click(document.querySelector<HTMLElement>('[data-slot="dialog-overlay"]')!, {
			position: {x: 5, y: 5},
		});

		await expect.element(screen.getByRole('dialog')).toBeVisible();
		expect(onClose).not.toHaveBeenCalled();
	});

	it('calls onClose when cancelled', async () => {
		const onClose = vi.fn();
		const screen = await render(
			<DeleteDefinitionModal
				isVisible
				title="warning"
				description="description"
				bodyContent={<div>body</div>}
				confirmationText="confirm"
				onClose={onClose}
				onDelete={() => {}}
			/>,
		);

		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}));

		expect(onClose).toHaveBeenCalledTimes(1);
	});

	it('renders the warning content when provided', async () => {
		const screen = await render(
			<DeleteDefinitionModal
				isVisible
				title="warning"
				description="description"
				bodyContent={<div>body</div>}
				confirmationText="confirm"
				warningTitle="Careful"
				warningContent={<div>this cannot be undone</div>}
				onClose={() => {}}
				onDelete={() => {}}
			/>,
		);

		await expect.element(screen.getByText('Careful')).toBeVisible();
		await expect.element(screen.getByText('this cannot be undone')).toBeVisible();
	});

	it('clears its confirmation state when cancelled and reopened', async () => {
		const onClose = vi.fn();
		const screen = await render(<ControlledDeleteDefinitionModal onClose={onClose} />);

		await userEvent.click(screen.getByText('confirm'));
		await expect.element(screen.getByRole('checkbox', {name: 'confirm'})).toBeChecked();
		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}));
		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
		await userEvent.click(screen.getByRole('button', {name: 'reopen'}));
		await expect.element(screen.getByRole('checkbox', {name: 'confirm'})).not.toBeChecked();
		await expect.element(screen.getByRole('button', {name: 'Delete'})).toBeDisabled();
	});

	it('clears its confirmation state after a successful delete', async () => {
		const onDelete = vi.fn();
		const screen = await render(<ControlledDeleteDefinitionModal onDelete={onDelete} />);

		await userEvent.click(screen.getByText('confirm'));
		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));
		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
		await userEvent.click(screen.getByRole('button', {name: 'reopen'}));
		await expect.element(screen.getByRole('checkbox', {name: 'confirm'})).not.toBeChecked();
		await expect.element(screen.getByRole('button', {name: 'Delete'})).toBeDisabled();
	});
});
