/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it, vi} from 'vitest';
import {renderHook} from 'vitest-browser-react';
import {useDebouncedUrlFilter} from './useDebouncedUrlFilter';

const DELAY = 30;

async function setup(initialApplied = '') {
	const onCommit = vi.fn<(value: string | undefined) => void>();
	const hook = await renderHook((props) => useDebouncedUrlFilter(props?.applied ?? initialApplied, onCommit, DELAY), {
		initialProps: {applied: initialApplied},
	});
	return {onCommit, ...hook};
}

describe('useDebouncedUrlFilter', () => {
	it('should start with the applied value as the draft and not commit', async () => {
		// given
		const {result, onCommit} = await setup('foo');

		// then
		expect(result.current[0]).toBe('foo');
		await new Promise((resolve) => setTimeout(resolve, DELAY * 2));
		expect(onCommit).not.toHaveBeenCalled();
	});

	it('should commit only the latest draft after the delay', async () => {
		// given
		const {result, onCommit} = await setup();

		// when
		result.current[1]('a');
		result.current[1]('ab');

		// then
		await expect.poll(() => onCommit.mock.calls).toEqual([['ab']]);
	});

	it('should commit an empty draft as undefined', async () => {
		// given
		const {result, onCommit} = await setup('foo');

		// when
		result.current[1]('');

		// then
		await expect.poll(() => onCommit.mock.calls).toEqual([[undefined]]);
	});

	it('should follow the applied value when the draft is unedited, e.g. on back/forward navigation', async () => {
		// given
		const {result, rerender} = await setup('foo');

		// when
		await rerender({applied: 'bar'});

		// then
		expect(result.current[0]).toBe('bar');
	});

	it('should keep newer typed input when an earlier commit is applied late', async () => {
		// given
		const {result, rerender, onCommit} = await setup('');
		result.current[1]('a');
		await expect.poll(() => onCommit.mock.calls).toEqual([['a']]);

		// when: the user keeps typing, then the URL catches up with the earlier commit
		result.current[1]('ab');
		await expect.poll(() => result.current[0]).toBe('ab');
		await rerender({applied: 'a'});

		// then
		expect(result.current[0]).toBe('ab');
	});
});
