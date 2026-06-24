/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

function isValidJSON(value: string) {
	try {
		JSON.parse(value);
		return true;
	} catch {
		return false;
	}
}

const customFiltersSchema = z.object({
	assignee: z.enum(['all', 'unassigned', 'me', 'user-and-group']).default('all'),
	assignedTo: z.string().trim().optional(),
	candidateGroup: z.string().trim().optional(),
	status: z.enum(['all', 'open', 'completed']).default('all'),
	bpmnProcess: z.string().optional(),
	tenant: z.string().optional(),
	dueDateFrom: z.coerce.date().optional(),
	dueDateTo: z.coerce.date().optional(),
	followUpDateFrom: z.coerce.date().optional(),
	followUpDateTo: z.coerce.date().optional(),
	taskId: z.string().trim().optional(),
	variables: z
		.array(
			z.object({
				name: z.string().trim().optional(),
				value: z
					.string()
					.trim()
					.transform((value) => (isValidJSON(value) ? JSON.stringify(JSON.parse(value), null, 0) : value))
					.optional(),
			}),
		)
		.transform((value) => value.filter(({name = '', value = ''}) => name.length > 0 && value.length > 0))
		.optional(),
});

const namedCustomFiltersSchema = customFiltersSchema.extend({
	name: z.string().trim().optional(),
});

type CustomFilters = z.infer<typeof customFiltersSchema>;
type NamedCustomFilters = z.infer<typeof namedCustomFiltersSchema>;

export {namedCustomFiltersSchema};
export type {CustomFilters, NamedCustomFilters};
