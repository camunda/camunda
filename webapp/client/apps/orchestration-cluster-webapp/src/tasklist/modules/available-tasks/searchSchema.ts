/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

const SORT_BY_VALUES = ['creation', 'follow-up', 'due', 'completion', 'priority'] as const;

const tasklistIndexSearchSchema = z.object({
	sortBy: z.enum(SORT_BY_VALUES).default('creation'),
});

type TasklistIndexSearch = z.infer<typeof tasklistIndexSearchSchema>;

const tasklistIndexSearchDefaults = {
	sortBy: 'creation',
} as const satisfies TasklistIndexSearch;

export {tasklistIndexSearchSchema, tasklistIndexSearchDefaults, type TasklistIndexSearch};
