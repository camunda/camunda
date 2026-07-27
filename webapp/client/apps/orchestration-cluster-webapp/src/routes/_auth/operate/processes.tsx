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
	tenantId: z.coerce.string().optional(),
	// coerce: small (safe-range) numeric-looking keys still arrive typed as a JS number from the
	// router's search parser (main.tsx's parseSearchValueSafe only keeps precision-losing large
	// keys as strings, it doesn't change the type of small ones) — normalize to string either way
	processInstanceKey: z.coerce.string().optional(),
	parentProcessInstanceKey: z.coerce.string().optional(),
	businessId: z.coerce.string().optional(),
	batchOperationKey: z.coerce.string().optional(),
	// coerce: the router's search parser can turn a numeric-looking error message (e.g. "404")
	// into a number, which a plain z.string() would reject — coerce keeps it valid either way
	errorMessage: z.coerce.string().optional(),
	hasRetriesLeft: z.boolean().optional(),
	startDateFrom: z.string().optional(),
	startDateTo: z.string().optional(),
	endDateFrom: z.string().optional(),
	endDateTo: z.string().optional(),
	active: z.boolean().default(true),
	incidents: z.boolean().default(true),
	completed: z.boolean().default(false),
	canceled: z.boolean().default(false),
});

export const Route = createFileRoute('/_auth/operate/processes')({
	validateSearch: processesSearchSchema,
	loader: ({context: {queryClient}}) =>
		queryClient.ensureQueryData(queries.queryProcessDefinitions({page: {limit: 1000}})),
	component: function ProcessesRoute() {
		return <Processes {...Route.useSearch()} />;
	},
});
