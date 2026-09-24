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

const processesSearchSchema = z
	.object({
		process: z.string().optional(),
		version: z.number().int().positive().optional(),
		processDefinitionId: z.string().optional(),
		processDefinitionVersion: z
			.union([z.coerce.number().int().positive(), z.literal('all')])
			.optional()
			.catch(undefined),
		elementId: z.string().optional(),
		tenantId: z.coerce.string().optional(),
		processInstanceKey: z.coerce.string().optional(),
		parentProcessInstanceKey: z.coerce.string().optional(),
		businessId: z.coerce.string().optional(),
		batchOperationKey: z.coerce.string().optional(),
		errorMessage: z.coerce.string().optional(),
		incidentErrorHashCode: z
			.union([
				z.number().int(),
				z
					.string()
					.regex(/^-?\d+$/)
					.transform(Number)
					.pipe(z.number().int()),
			])
			.optional()
			.catch(undefined),
		hasRetriesLeft: z.boolean().optional(),
		startDateFrom: z.string().optional(),
		startDateTo: z.string().optional(),
		endDateFrom: z.string().optional(),
		endDateTo: z.string().optional(),
		active: z.boolean().default(true),
		incidents: z.boolean().default(true),
		completed: z.boolean().default(false),
		canceled: z.boolean().default(false),
		suspended: z.boolean().default(true),
		sort: z.string().optional(),
	})
	.transform(({processDefinitionId, processDefinitionVersion, ...search}) => ({
		...search,
		process: search.process ?? processDefinitionId,
		version: search.version ?? (processDefinitionVersion === 'all' ? undefined : processDefinitionVersion),
	}));

const Route = createFileRoute('/_carbon/_auth/operate/processes/')({
	validateSearch: processesSearchSchema,
	loader: ({context: {queryClient}}) =>
		queryClient.ensureQueryData(queries.queryProcessDefinitions({page: {limit: 1000}})),
	component: function ProcessesRoute() {
		return <Processes {...Route.useSearch()} />;
	},
});

export {Route, processesSearchSchema};
