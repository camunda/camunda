/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {DeleteConfirmationModal} from './DeleteConfirmationModal';

const PROCESS_INSTANCE_KEY = 'instance_1';

describe('<DeleteConfirmationModal />', () => {
	it('should call onConfirm without also calling onCancel when the delete button is clicked', async () => {
		const onConfirm = vi.fn();
		const onCancel = vi.fn();

		const screen = await render(
			<DeleteConfirmationModal
				processInstanceKey={PROCESS_INSTANCE_KEY}
				open
				onConfirm={onConfirm}
				onCancel={onCancel}
			/>,
		);

		await userEvent.click(screen.getByRole('button', {name: /^delete$/i}));

		expect(onConfirm).toHaveBeenCalledOnce();
		expect(onCancel).not.toHaveBeenCalled();
	});

	it('should call onCancel without calling onConfirm when the cancel button is clicked', async () => {
		const onConfirm = vi.fn();
		const onCancel = vi.fn();

		const screen = await render(
			<DeleteConfirmationModal
				processInstanceKey={PROCESS_INSTANCE_KEY}
				open
				onConfirm={onConfirm}
				onCancel={onCancel}
			/>,
		);

		await userEvent.click(screen.getByRole('button', {name: 'Cancel', exact: true}));

		expect(onCancel).toHaveBeenCalledOnce();
		expect(onConfirm).not.toHaveBeenCalled();
	});
});
