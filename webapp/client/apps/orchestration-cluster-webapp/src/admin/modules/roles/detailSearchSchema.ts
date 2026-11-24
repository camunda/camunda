/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {MEMBER_KINDS, type MemberKind, type MemberPage} from './members';
import {DEFAULT_PAGE_SIZE, PAGE_SIZES, escapeWildcards} from './searchSchema';

const roleDetailSearchSchema = z.object({
	tab: z.enum(MEMBER_KINDS).optional(),
	search: z.coerce.string().optional(),
	page: z.number().int().positive().optional(),
	pageSize: z.literal(PAGE_SIZES).optional(),
});

type RoleDetailSearch = z.infer<typeof roleDetailSearchSchema>;
type RoleDetailSearchChange = (next: Partial<RoleDetailSearch>, options?: {replace?: boolean}) => void;

function getRoleMembersRequestBody(search: RoleDetailSearch, kind: MemberKind): MemberPage {
	const pageSize = search.pageSize ?? DEFAULT_PAGE_SIZE;
	const searchTerm = search.search?.trim();
	const page = {from: ((search.page ?? 1) - 1) * pageSize, limit: pageSize};

	if (kind !== 'users' || searchTerm === undefined || searchTerm === '') {
		return {page};
	}

	return {page, filter: {username: {$like: `*${escapeWildcards(searchTerm)}*`}}};
}

export {getRoleMembersRequestBody, roleDetailSearchSchema};
export type {RoleDetailSearch, RoleDetailSearchChange};
