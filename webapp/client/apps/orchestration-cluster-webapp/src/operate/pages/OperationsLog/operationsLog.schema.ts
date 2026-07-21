/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {
	auditLogEntityTypeSchema,
	auditLogOperationTypeSchema,
	auditLogResultSchema,
} from '@camunda/camunda-api-zod-schemas/8.10';

const operationsLogSearchSchema = z.object({
	process: z.string().optional(),
	version: z.number().int().positive().optional(),
	processInstanceKey: z.coerce.string().optional(),
	operationType: z.array(auditLogOperationTypeSchema).optional(),
	entityType: z.array(auditLogEntityTypeSchema).optional(),
	result: auditLogResultSchema.optional(),
	// coerce: small (safe-range) numeric-looking values still arrive typed as a JS number from the
	// router's search parser — normalize to string either way, matching the Processes route schema.
	actorId: z.coerce.string().optional(),
	timestampAfter: z.string().optional(),
	timestampBefore: z.string().optional(),
	tenantId: z.coerce.string().optional(),
	sort: z.string().optional(),
});

type OperationsLogSearch = z.infer<typeof operationsLogSearchSchema>;

export {operationsLogSearchSchema};
export type {OperationsLogSearch};
