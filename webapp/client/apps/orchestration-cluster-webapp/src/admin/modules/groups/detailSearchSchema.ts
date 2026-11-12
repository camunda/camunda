/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {MEMBER_KINDS, type MemberPage} from './members';
import {DEFAULT_PAGE_SIZE, PAGE_SIZES} from './searchSchema';

const groupDetailSearchSchema = z.object({
	tab: z.enum(MEMBER_KINDS).optional(),
	page: z.number().int().positive().optional(),
	pageSize: z.literal(PAGE_SIZES).optional(),
});

type GroupDetailSearch = z.infer<typeof groupDetailSearchSchema>;
type GroupDetailSearchChange = (next: Partial<GroupDetailSearch>, options?: {replace?: boolean}) => void;

function getGroupMembersRequestBody(search: GroupDetailSearch): MemberPage {
	const pageSize = search.pageSize ?? DEFAULT_PAGE_SIZE;

	return {page: {from: ((search.page ?? 1) - 1) * pageSize, limit: pageSize}};
}

export {getGroupMembersRequestBody, groupDetailSearchSchema};
export type {GroupDetailSearch, GroupDetailSearchChange};
