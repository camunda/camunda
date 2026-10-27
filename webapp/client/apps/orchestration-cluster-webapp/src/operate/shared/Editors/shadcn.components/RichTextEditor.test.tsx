/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup, render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {RichTextEditor} from './RichTextEditor';

describe('RichTextEditor', () => {
	afterEach(async () => {
		await cleanup();
	});

	it('should expose the editor id and invalid state on the Monaco input', async () => {
		const screen = await render(
			<RichTextEditor id="variable-value" value="true" isInvalid onChange={vi.fn()} autoFocus={false} />,
		);
		const editor = screen.getByRole('textbox', {name: 'Value', exact: true});

		await expect.element(editor).toHaveAttribute('id', 'variable-value-editor');
		await expect.element(editor).toHaveAttribute('aria-invalid', 'true');
	});

	it('should report edits through onChange', async () => {
		const onChange = vi.fn();
		const screen = await render(
			<RichTextEditor id="variable-value" value="true" onChange={onChange} autoFocus={false} />,
		);

		await userEvent.click(screen.getByText('true', {exact: true}));
		await userEvent.keyboard('{End}x');

		await expect.poll(() => onChange).toHaveBeenCalledWith('truex');
	});
});
