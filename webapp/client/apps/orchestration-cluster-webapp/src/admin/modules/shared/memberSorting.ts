/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import type {SortingConfig} from '@camunda/design-system';

type SortingState = NonNullable<SortingConfig['sortState']>;

const MEMBER_SORT_FIELDS = [
	'username',
	'clientId',
	'groupId',
	'roleId',
	'mappingRuleId',
	'name',
	'claimName',
	'claimValue',
] as const;

const memberSortSearchShape = {
	sortField: z.enum(MEMBER_SORT_FIELDS).optional(),
	sortOrder: z.enum(['ASC', 'DESC']).optional(),
};

type MemberSortField = (typeof MEMBER_SORT_FIELDS)[number];
type MemberSortSearch = {sortField?: MemberSortField; sortOrder?: 'ASC' | 'DESC'};

function getMemberSort(search: MemberSortSearch, sortableFields: readonly MemberSortField[]) {
	const {sortField} = search;
	return sortField !== undefined && sortableFields.includes(sortField)
		? {sort: [{field: sortField, order: search.sortOrder ?? 'ASC'}]}
		: undefined;
}

type MemberSortRequest = {field: MemberSortField; order: 'ASC' | 'DESC'}[];

function narrowMemberSort<Field extends MemberSortField, Body extends {sort?: MemberSortRequest}>(
	{sort, ...rest}: Body,
	supportedFields: readonly Field[],
): Omit<Body, 'sort'> & {sort?: {field: Field; order: 'ASC' | 'DESC'}[]} {
	const supported = sort?.flatMap(({field, order}) => {
		const supportedField = supportedFields.find((candidate) => candidate === field);
		return supportedField === undefined ? [] : [{field: supportedField, order}];
	});
	return supported === undefined || supported.length === 0 ? rest : {...rest, sort: supported};
}

function getMemberSortState(search: MemberSortSearch, sortableFields: readonly MemberSortField[]): SortingState {
	const {sortField} = search;
	return sortField !== undefined && sortableFields.includes(sortField)
		? [{id: sortField, desc: search.sortOrder === 'DESC'}]
		: [];
}

function toMemberSortSearch(state: SortingState): MemberSortSearch & {page: undefined} {
	const [sorted] = state;
	return {
		sortField: sorted === undefined ? undefined : (sorted.id as MemberSortField),
		sortOrder: sorted === undefined ? undefined : sorted.desc ? 'DESC' : 'ASC',
		page: undefined,
	};
}

export {getMemberSort, getMemberSortState, memberSortSearchShape, narrowMemberSort, toMemberSortSearch};
export type {MemberSortField, MemberSortRequest, MemberSortSearch, SortingState};
