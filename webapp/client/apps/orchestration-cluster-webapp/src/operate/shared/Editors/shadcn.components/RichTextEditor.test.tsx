/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterAll, afterEach, beforeAll, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup, render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {RichTextEditor} from './RichTextEditor';

describe('RichTextEditor', () => {
	beforeAll(() => {
		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout', 'Date'], shouldAdvanceTime: true});
	});
	afterAll(() => vi.useRealTimers());
	afterEach(async () => {
		await vi.advanceTimersByTimeAsync(100);
		await cleanup();
	});

	it('should preserve the Monaco input contract when editing in the design system', async () => {
		const onChange = vi.fn();
		const screen = await render(
			<RichTextEditor id="variable-value" value="true" isInvalid onChange={onChange} autoFocus={false} />,
		);
		const editor = screen.getByRole('textbox', {name: 'Value', exact: true});

		await expect.element(editor).toHaveAttribute('id', 'variable-value-editor');
		await expect.element(editor).toHaveAttribute('aria-invalid', 'true');
		await userEvent.click(screen.getByText('true', {exact: true}));
		await userEvent.keyboard('{End}x');

		expect(onChange).toHaveBeenCalledWith('truex');
	});
});
