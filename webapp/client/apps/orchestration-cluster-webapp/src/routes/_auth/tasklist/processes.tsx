/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TasklistProcessesPage} from '#/tasklist/pages/TasklistProcessesPage';
import {createFileRoute} from '@tanstack/react-router';
import {z} from 'zod';

export const Route = createFileRoute('/_auth/tasklist/processes')({
	validateSearch: z.object({
		search: z.string().optional(),
		hasStartForm: z.enum(['yes', 'no']).optional(),
		tenantId: z.string().optional(),
	}),
	component: TasklistProcessesPage,
});
