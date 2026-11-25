/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {getMemberSort, getMemberSortState, toMemberSortSearch} from './memberSorting';

const SORTABLE_FIELDS = ['roleId', 'name'] as const;

describe('getMemberSort', () => {
	it('should ignore fields the member kind cannot sort on', () => {
		expect(getMemberSort({sortField: 'claimName'}, SORTABLE_FIELDS)).toBeUndefined();
	});
});

describe('getMemberSortState', () => {
	it('should ignore fields the member kind cannot sort on', () => {
		expect(getMemberSortState({sortField: 'name', sortOrder: 'DESC'}, ['roleId'])).toEqual([]);
	});
});

describe('toMemberSortSearch', () => {
	it('should clear sorting and the page when sorting is removed', () => {
		expect(toMemberSortSearch([])).toEqual({sortField: undefined, sortOrder: undefined, page: undefined});
	});
});
