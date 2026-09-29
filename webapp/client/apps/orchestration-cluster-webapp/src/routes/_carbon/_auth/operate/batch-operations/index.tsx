/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';
import {t} from 'i18next';
import {BatchOperations} from '#/operate/pages/BatchOperations/BatchOperations';
import {
	batchOperationsOptions,
	batchOperationsSearchSchema,
} from '#/operate/pages/BatchOperations/batchOperations.queries';

export const Route = createFileRoute('/_carbon/_auth/operate/batch-operations/')({
	validateSearch: batchOperationsSearchSchema,
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
