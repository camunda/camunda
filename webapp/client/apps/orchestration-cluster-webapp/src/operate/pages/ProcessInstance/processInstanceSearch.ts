/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

const processInstanceSelectionSchema = z.object({
	elementId: z.coerce.string().optional(),
	elementInstanceKey: z.coerce.string().optional(),
	isMultiInstanceBody: z.boolean().optional(),
	isPlaceholder: z.boolean().optional(),
	anchorElementId: z.coerce.string().optional(),
});
const processInstanceSearchSchema = processInstanceSelectionSchema.extend({
	tab: z
		.enum([
			'variables',
			'incidents',
			'details',
			'history',
			'input-mappings',
			'output-mappings',
			'listeners',
			'operations-log',
		])
		.optional()
		.catch(undefined),
});

type ProcessInstanceSearch = z.infer<typeof processInstanceSearchSchema>;
type ProcessInstanceSelection = z.infer<typeof processInstanceSelectionSchema>;
type ProcessInstanceTab = NonNullable<ProcessInstanceSearch['tab']>;

export {
	processInstanceSearchSchema,
	type ProcessInstanceSearch,
	type ProcessInstanceSelection,
	type ProcessInstanceTab,
};
