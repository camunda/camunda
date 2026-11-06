/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {getHiddenTabRedirect, getVisibleProcessInstanceTabs} from './processInstanceTabs';

describe('getHiddenTabRedirect', () => {
	const withoutSelection = getVisibleProcessInstanceTabs(false);
	const withSelection = getVisibleProcessInstanceTabs(true);

	it.for([
		{
			name: 'a visible tab',
			input: {
				activeTab: 'listeners',
				visibleTabs: withoutSelection,
				hasSelection: false,
				isProcessLevelWaiting: false,
				isWaitStateLoading: false,
			},
			expected: null,
		},
		{
			name: 'a mappings tab after the selection is cleared',
			input: {
				activeTab: 'input-mappings',
				visibleTabs: withoutSelection,
				hasSelection: false,
				isProcessLevelWaiting: false,
				isWaitStateLoading: false,
			},
			expected: 'variables',
		},
		{
			name: 'a mappings tab after the selection is cleared on a process-level wait state',
			input: {
				activeTab: 'output-mappings',
				visibleTabs: withoutSelection,
				hasSelection: false,
				isProcessLevelWaiting: true,
				isWaitStateLoading: false,
			},
			expected: 'details',
		},
		{
			name: 'a mappings tab while the wait states are loading',
			input: {
				activeTab: 'input-mappings',
				visibleTabs: withoutSelection,
				hasSelection: false,
				isProcessLevelWaiting: false,
				isWaitStateLoading: true,
			},
			expected: null,
		},
		{
			name: 'a hidden tab with a selection',
			input: {
				activeTab: 'input-mappings',
				visibleTabs: withSelection.filter((tab) => tab !== 'input-mappings'),
				hasSelection: true,
				isProcessLevelWaiting: false,
				isWaitStateLoading: true,
			},
			expected: 'details',
		},
	] as const)('should resolve $name', ({input, expected}) => {
		expect(getHiddenTabRedirect(input)).toBe(expected);
	});
});
