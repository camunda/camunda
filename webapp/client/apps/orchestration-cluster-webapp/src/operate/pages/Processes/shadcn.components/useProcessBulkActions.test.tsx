/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, vi} from 'vitest';
import {act} from 'react';
import {cleanup, renderHook} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import type {ProcessInstancesSelection} from '../useProcessInstancesSelection';
import {useProcessBulkActions} from './useProcessBulkActions';

function createSelection(selectedCount: number): ProcessInstancesSelection {
	return {
		selectedCount,
		isCountTruncated: false,
		filterIdentity: 'filter',
		eligibility: {delete: true, cancel: true, retry: true, suspend: true, resume: true},
		reset: vi.fn(),
	} as unknown as ProcessInstancesSelection;
}

describe('useProcessBulkActions', () => {
	afterEach(async () => {
		await cleanup();
	});

	it('should not reopen a confirmation after the selected count dips below zero', async () => {
		// given
		const {result, rerender} = await renderHook(
			(props?: {selection: ProcessInstancesSelection}) =>
				useProcessBulkActions({selection: props!.selection, isSubmitting: false, onSubmit: vi.fn()}),
			{initialProps: {selection: createSelection(2)}},
		);
		act(() => {
			result.current.batchActions.find(({id}) => id === 'cancel')?.onClick();
		});
		expect(result.current.dialogProps.action).toBe('cancel');

		// when
		await rerender({selection: createSelection(-1)});
		await rerender({selection: createSelection(2)});

		// then
		expect(result.current.dialogProps.action).toBeNull();
	});
});
