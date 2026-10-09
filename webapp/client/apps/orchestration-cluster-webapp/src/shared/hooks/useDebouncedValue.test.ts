/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {renderHook} from 'vitest-browser-react';
import {useDebouncedValue} from './useDebouncedValue';

describe('useDebouncedValue', () => {
	it('should return the initial value immediately', async () => {
		// given
		const {result} = await renderHook(() => useDebouncedValue('initial', 20));

		// then
		expect(result.current).toBe('initial');
	});

	it('should only publish the latest value once the delay has elapsed', async () => {
		// given
		const {result, rerender} = await renderHook((props) => useDebouncedValue(props?.value ?? 'a', 50), {
			initialProps: {value: 'a'},
		});

		// when
		await rerender({value: 'ab'});
		await rerender({value: 'abc'});

		// then
		expect(result.current).toBe('a');
		await expect.poll(() => result.current).toBe('abc');
	});
});
