/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {
	auditLogResultSchema,
	type AuditLogEntityType,
	type AuditLogOperationType,
} from '@camunda/camunda-api-zod-schemas/8.11';

const PAGE_SIZES = [50, 100, 200] as const;
const DEFAULT_PAGE_SIZE = 50;

// Deliberately a curated subset of the full 14-value enums, relevant to the ADMIN
// audit-log category this page shows — Identity's ALLOWED_OPERATION_TYPES, plus the
// COMPLETE and CANCEL entries that end a probe.
const ALLOWED_OPERATION_TYPES = [
	'CREATE',
	'ASSIGN',
	'UNASSIGN',
	'DELETE',
	'UPDATE',
	'COMPLETE',
	'CANCEL',
] as const satisfies readonly AuditLogOperationType[];

// Mirrors Identity's ALLOWED_ENTITY_TYPES.
const ALLOWED_RELATED_ENTITY_TYPES = [
	'AUTHORIZATION',
	'ROLE',
	'USER',
	'GROUP',
	'MAPPING_RULE',
	'TENANT',
] as const satisfies readonly AuditLogEntityType[];

// A probe writes ADMIN entries too, but never owns an authorization, so it is no related entity type.
const ALLOWED_ENTITY_TYPES = [
	...ALLOWED_RELATED_ENTITY_TYPES,
	'PROBE',
] as const satisfies readonly AuditLogEntityType[];

const ALLOWED_RESULT_TYPES = auditLogResultSchema.options;

// The columns the table can be sorted by.
const SORTABLE_FIELDS = ['operationType', 'entityType', 'actorId', 'timestamp'] as const;

const operationsLogSearchSchema = z.object({
	operationType: z.enum(ALLOWED_OPERATION_TYPES).optional(),
	entityType: z.enum(ALLOWED_ENTITY_TYPES).optional(),
	relatedEntityType: z.enum(ALLOWED_RELATED_ENTITY_TYPES).optional(),
	// coerce: a numeric-looking owner key arrives typed as a JS number from the router's
	// search parser, matching the MCP Processes route schema.
	relatedEntityKey: z.coerce.string().optional(),
	result: z.enum(ALLOWED_RESULT_TYPES).optional(),
	actor: z.coerce.string().optional(),
	timestampFrom: z.iso.datetime().optional().catch(undefined),
	timestampTo: z.iso.datetime().optional().catch(undefined),
	sortField: z.enum(SORTABLE_FIELDS).optional(),
	sortOrder: z.enum(['ASC', 'DESC']).optional(),
	page: z.number().int().positive().optional(),
	pageSize: z.literal(PAGE_SIZES).optional(),
});

type OperationsLogSearch = z.infer<typeof operationsLogSearchSchema>;

export {
	ALLOWED_ENTITY_TYPES,
	ALLOWED_OPERATION_TYPES,
	ALLOWED_RELATED_ENTITY_TYPES,
	ALLOWED_RESULT_TYPES,
	DEFAULT_PAGE_SIZE,
	PAGE_SIZES,
	operationsLogSearchSchema,
};
export type {OperationsLogSearch};
