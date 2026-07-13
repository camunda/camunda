/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';
import {z} from 'zod';
import {queries} from '#/shared/http/queries';
import {Processes} from '#/operate/pages/Processes/Processes';

const processesSearchSchema = z.object({
	process: z.string().optional(),
	version: z.number().int().positive().optional(),
	elementId: z.string().optional(),
	errorMessage: z.string().optional(),
	active: z.boolean().default(true),
	incidents: z.boolean().default(true),
	completed: z.boolean().default(false),
	canceled: z.boolean().default(false),
});

export const Route = createFileRoute('/_auth/operate/processes')({
	validateSearch: processesSearchSchema,
	loader: ({context: {queryClient}}) => queryClient.ensureQueryData(queries.queryProcessDefinitions({})),
	component: function ProcessesRoute() {
		const search = Route.useSearch();
		return <Processes {...search} />;
	},
});
