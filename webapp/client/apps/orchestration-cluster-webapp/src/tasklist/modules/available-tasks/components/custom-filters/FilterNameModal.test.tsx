/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {FilterNameModal} from './FilterNameModal';

describe('<FilterNameModal />', () => {
	it('should call onApply with the entered name on save and apply', async () => {
		const mockOnApply = vi.fn();
		const screen = await render(<FilterNameModal isOpen onApply={mockOnApply} onCancel={() => {}} />);

		await userEvent.fill(screen.getByRole('textbox', {name: /filter name/i}), 'My filter');
		await userEvent.click(screen.getByRole('button', {name: /save and apply/i}));

		expect(mockOnApply).toHaveBeenCalledOnce();
		expect(mockOnApply).toHaveBeenCalledWith('My filter');
	});

	it('should show a validation error when submitting with an empty name', async () => {
		const mockOnApply = vi.fn();
		const screen = await render(<FilterNameModal isOpen onApply={mockOnApply} onCancel={() => {}} />);

		await userEvent.click(screen.getByRole('button', {name: /save and apply/i}));

		expect(mockOnApply).not.toHaveBeenCalled();
		await expect.element(screen.getByRole('textbox', {name: /filter name/i})).toHaveAttribute('aria-invalid', 'true');
	});

	it('should call onCancel on cancel', async () => {
		const mockOnCancel = vi.fn();
		const screen = await render(<FilterNameModal isOpen onApply={() => {}} onCancel={mockOnCancel} />);

		await userEvent.click(screen.getByRole('button', {name: /cancel/i}));

		expect(mockOnCancel).toHaveBeenCalledOnce();
	});
});
