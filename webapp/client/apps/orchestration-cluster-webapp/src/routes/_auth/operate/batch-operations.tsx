/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';
import {z} from 'zod';
import {BatchOperations} from '#/operate/pages/BatchOperations/BatchOperations';
import {batchOperationsOptions} from '#/operate/pages/BatchOperations/batchOperations.queries';

const batchOperationsSearchSchema = z.object({
	page: z.number().int().positive().default(1),
	pageSize: z.number().int().positive().default(20),
	sort: z.string().optional(),
});

export const Route = createFileRoute('/_auth/operate/batch-operations')({
	validateSearch: batchOperationsSearchSchema,
	loaderDeps: ({search}) => ({...search}),
	loader: ({context: {queryClient}, deps}) => queryClient.ensureQueryData(batchOperationsOptions(deps)),
	component: function BatchOperationsRoute() {
		const {page, pageSize, sort} = Route.useSearch();
		return <BatchOperations page={page} pageSize={pageSize} sort={sort} />;
	},
});
