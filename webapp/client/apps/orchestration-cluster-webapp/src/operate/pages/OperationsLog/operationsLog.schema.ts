/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import type {SearchMiddleware} from '@tanstack/react-router';
import {
	auditLogEntityTypeSchema,
	auditLogOperationTypeSchema,
	auditLogResultSchema,
} from '@camunda/camunda-api-zod-schemas/8.11';

const commaSeparated = <T extends z.ZodType>(item: T) =>
	z.preprocess((value) => (typeof value === 'string' ? value.split(',') : value), z.array(item).optional());

const versionSchema = z.union([
	z.number().int().positive(),
	z.string().regex(/^\d+$/).transform(Number).pipe(z.number().int().positive()),
]);

const operationsLogSearchSchema = z
	.object({
		process: z.coerce.string().optional(),
		version: versionSchema.optional(),
		allVersions: z.boolean().optional(),
		processDefinitionId: z.coerce.string().optional(),
		processDefinitionVersion: z.union([versionSchema, z.literal('all')]).optional(),
		processInstanceKey: z.coerce.string().optional(),
		operationType: commaSeparated(auditLogOperationTypeSchema),
		entityType: commaSeparated(auditLogEntityTypeSchema),
		result: auditLogResultSchema.optional(),
		// coerce: small (safe-range) numeric-looking values still arrive typed as a JS number from the
		// router's search parser — normalize to string either way, matching the Processes route schema.
		actorId: z.coerce.string().optional(),
		timestampAfter: z.string().optional(),
		timestampBefore: z.string().optional(),
		tenantId: z.coerce.string().optional(),
		sort: z.string().optional(),
	})
	.transform(({processDefinitionId, processDefinitionVersion, ...search}) => ({
		...search,
		...(search.process === undefined && processDefinitionId !== undefined ? {process: processDefinitionId} : {}),
		...(search.version === undefined && typeof processDefinitionVersion === 'number'
			? {version: processDefinitionVersion}
			: {}),
		...(search.version === undefined && processDefinitionVersion === 'all' ? {allVersions: true} : {}),
	}));

type OperationsLogSearch = z.infer<typeof operationsLogSearchSchema>;

const stripLegacyFilters: SearchMiddleware<OperationsLogSearch> = ({search, next}) => {
	const withoutAliases = {...search, processDefinitionId: undefined, processDefinitionVersion: undefined};
	return next(withoutAliases);
};

export {operationsLogSearchSchema, stripLegacyFilters};
export type {OperationsLogSearch};
