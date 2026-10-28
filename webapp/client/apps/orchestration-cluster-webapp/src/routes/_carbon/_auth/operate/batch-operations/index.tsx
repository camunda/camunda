/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, stripSearchParams, type SearchMiddleware} from '@tanstack/react-router';
import {t} from 'i18next';
import {BatchOperations} from '#/operate/pages/BatchOperations/BatchOperations';
import {
	batchOperationsOptions,
	batchOperationsSearchSchema,
} from '#/operate/pages/BatchOperations/batchOperations.queries';

const lowercaseSortOrder: SearchMiddleware<ReturnType<typeof batchOperationsSearchSchema.parse>> = ({search, next}) => {
	const result = next(search);
	return result.sort === undefined
		? result
		: {...result, sort: result.sort.replace(/\+(ASC|DESC)$/, (order) => order.toLowerCase())};
};

export const Route = createFileRoute('/_carbon/_auth/operate/batch-operations/')({
	validateSearch: batchOperationsSearchSchema,
	search: {middlewares: [lowercaseSortOrder, stripSearchParams(batchOperationsSearchSchema.parse({}))]},
	loaderDeps: ({search}) => ({...search}),
	loader: ({context: {queryClient}, deps}) => {
		void queryClient.prefetchQuery(batchOperationsOptions(deps));
	},
	head: () => ({meta: [{title: t('operate.batchOperations.pageTitle')}]}),
	component: function BatchOperationsRoute() {
		const {page, pageSize, sort} = Route.useSearch();
		return <BatchOperations page={page} pageSize={pageSize} sort={sort} />;
	},
});
