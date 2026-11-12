/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {getHiddenTabRedirect, getVisibleProcessInstanceTabs} from './processInstanceTabs';

describe('getVisibleProcessInstanceTabs', () => {
	it.for([
		{
			name: 'no incident, no selection, and no process-level wait state',
			input: {hasIncidentsTab: false, hasSelection: false, isProcessLevelWaiting: false},
			expected: ['variables', 'listeners', 'operations-log'],
		},
		{
			name: 'an incident without a selection',
			input: {hasIncidentsTab: true, hasSelection: false, isProcessLevelWaiting: false},
			expected: ['variables', 'incidents', 'listeners', 'operations-log'],
		},
		{
			name: 'a selection without an incident',
			input: {hasIncidentsTab: false, hasSelection: true, isProcessLevelWaiting: false},
			expected: ['variables', 'input-mappings', 'output-mappings', 'listeners', 'operations-log', 'details'],
		},
		{
			name: 'a process-level wait state without a selection',
			input: {hasIncidentsTab: false, hasSelection: false, isProcessLevelWaiting: true},
			expected: ['variables', 'listeners', 'operations-log', 'details'],
		},
		{
			name: 'an incident, a selection, and a process-level wait state',
			input: {hasIncidentsTab: true, hasSelection: true, isProcessLevelWaiting: true},
			expected: [
				'variables',
				'input-mappings',
				'output-mappings',
				'incidents',
				'listeners',
				'operations-log',
				'details',
			],
		},
	] as const)('should show $expected for $name', ({input, expected}) => {
		expect([...getVisibleProcessInstanceTabs(input)].sort()).toEqual([...expected].sort());
	});
});

describe('getVisibleProcessInstanceTabs history tab', () => {
	it.for([
		{name: 'mobile', isMobile: true, expected: true},
		{name: 'desktop', isMobile: false, expected: false},
	])('should set history visibility to $expected on $name', ({isMobile, expected}) => {
		const tabs = getVisibleProcessInstanceTabs({
			hasIncidentsTab: false,
			hasSelection: false,
			isProcessLevelWaiting: false,
			isMobile,
		});

		expect(tabs.includes('history')).toBe(expected);
	});
});

describe('getHiddenTabRedirect', () => {
	const noExtras = getVisibleProcessInstanceTabs({
		hasIncidentsTab: false,
		hasSelection: false,
		isProcessLevelWaiting: false,
	});

	const withIncidents = getVisibleProcessInstanceTabs({
		hasIncidentsTab: true,
		hasSelection: false,
		isProcessLevelWaiting: false,
	});

	it.for([
		{
			name: 'a visible tab',
			input: {
				activeTab: 'listeners',
				visibleTabs: noExtras,
				hasSelection: false,
				isProcessLevelWaiting: false,
				isWaitStateLoading: false,
			},
			expected: null,
		},
		{
			name: 'a hidden incidents tab',
			input: {
				activeTab: 'incidents',
				visibleTabs: noExtras,
				hasSelection: false,
				isProcessLevelWaiting: false,
				isWaitStateLoading: false,
			},
			expected: 'variables',
		},
		{
			name: 'a hidden details tab',
			input: {
				activeTab: 'details',
				visibleTabs: noExtras,
				hasSelection: false,
				isProcessLevelWaiting: false,
				isWaitStateLoading: false,
			},
			expected: 'variables',
		},
		{
			name: 'a hidden incidents tab on a process-level wait state',
			input: {
				activeTab: 'incidents',
				visibleTabs: noExtras,
				hasSelection: false,
				isProcessLevelWaiting: true,
				isWaitStateLoading: false,
			},
			expected: 'details',
		},
		{
			name: 'a hidden incidents tab with a selection',
			input: {
				activeTab: 'incidents',
				visibleTabs: noExtras,
				hasSelection: true,
				isProcessLevelWaiting: false,
				isWaitStateLoading: false,
			},
			expected: 'details',
		},
		{
			name: 'a hidden history tab with an incident',
			input: {
				activeTab: 'history',
				visibleTabs: withIncidents,
				hasSelection: false,
				isProcessLevelWaiting: false,
				isWaitStateLoading: false,
			},
			expected: 'incidents',
		},
		{
			name: 'a hidden history tab without an incident',
			input: {
				activeTab: 'history',
				visibleTabs: noExtras,
				hasSelection: false,
				isProcessLevelWaiting: false,
				isWaitStateLoading: false,
			},
			expected: 'variables',
		},
		{
			name: 'a hidden details tab while the wait states are loading',
			input: {
				activeTab: 'details',
				visibleTabs: noExtras,
				hasSelection: false,
				isProcessLevelWaiting: false,
				isWaitStateLoading: true,
			},
			expected: null,
		},
	] as const)('should resolve $name', ({input, expected}) => {
		expect(getHiddenTabRedirect(input)).toBe(expected);
	});
});
