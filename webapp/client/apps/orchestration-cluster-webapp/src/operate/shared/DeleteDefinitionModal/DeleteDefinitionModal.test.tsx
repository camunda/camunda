/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect, vi} from 'vitest';
import {render} from 'vitest-browser-react';
import {DeleteDefinitionModal} from './DeleteDefinitionModal';

describe('<DeleteDefinitionModal />', () => {
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

		await expect.element(screen.getByRole('checkbox')).not.toBeChecked();
	});

	it('shows a validation error and does not delete when submitted unconfirmed', async () => {
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

		await screen.getByRole('button', {name: 'Delete'}).click();

		await expect.element(screen.getByText('Please tick this box if you want to proceed.')).toBeVisible();
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

		// the checkbox's native input is visually covered by its own label, which fails
		// real-browser hover actionability - click the visible label text instead.
		await screen.getByText('confirm').click();
		await screen.getByRole('button', {name: 'Delete'}).click();

		expect(onDelete).toHaveBeenCalledTimes(1);
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

		await screen.getByRole('button', {name: 'Cancel'}).click();

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
});
