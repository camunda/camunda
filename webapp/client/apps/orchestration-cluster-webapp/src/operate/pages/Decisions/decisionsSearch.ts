/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import type {DecisionsSearch} from './decisionsFilter';

const decisionsSearchSchema = z.object({
	decisionDefinitionId: z.string().optional(),
	decisionDefinitionVersion: z.union([z.number().int().positive(), z.literal('all')]).optional(),
	tenantId: z.coerce.string().optional(),
	evaluated: z.boolean().optional(),
	failed: z.boolean().optional(),
	decisionEvaluationInstanceKey: z.coerce.string().optional(),
	processInstanceKey: z.coerce.string().optional(),
	businessId: z.string().optional(),
	evaluationDateFrom: z.string().optional(),
	evaluationDateTo: z.string().optional(),
	sort: z.string().optional(),
});

function validateDecisionsSearch(search: unknown): DecisionsSearch {
	const {decisionDefinitionVersion, evaluated, failed, ...filters} = decisionsSearchSchema.parse(search);
	return {
		...filters,
		decisionDefinitionVersion: decisionDefinitionVersion === 'all' ? undefined : decisionDefinitionVersion,
		evaluated: evaluated ?? false,
		failed: failed ?? false,
	};
}

export {validateDecisionsSearch};
