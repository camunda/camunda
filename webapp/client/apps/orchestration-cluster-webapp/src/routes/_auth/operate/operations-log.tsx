/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {DataTableSkeleton} from '@carbon/react';
import {createFileRoute} from '@tanstack/react-router';
import {queries} from '#/shared/http/queries';
import {OperationsLog} from '#/operate/pages/OperationsLog/OperationsLog';
import {operationsLogSearchSchema} from '#/operate/pages/OperationsLog/operationsLog.schema';

export const Route = createFileRoute('/_auth/operate/operations-log')({
	validateSearch: operationsLogSearchSchema,
	loader: ({context: {queryClient}}) =>
		Promise.all([
			queryClient.ensureQueryData(queries.queryProcessDefinitions({page: {limit: 1000}})),
			queryClient.ensureQueryData(queries.queryDecisionDefinitions({page: {limit: 1000}})),
		]),
	pendingComponent: () => <DataTableSkeleton columnCount={7} rowCount={5} showHeader={false} showToolbar={false} />,
	component: function OperationsLogRoute() {
		return <OperationsLog {...Route.useSearch()} />;
	},
});
