/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';
import {queries} from '#/shared/http/queries';
import {Processes} from '#/operate/pages/Processes/Processes';
import {processesSearchSchema, stripLegacyProcessFilters} from '#/operate/pages/Processes/processesFilter';

const Route = createFileRoute('/_carbon/_auth/operate/processes/')({
	validateSearch: processesSearchSchema,
	search: {middlewares: [stripLegacyProcessFilters]},
	loader: ({context: {queryClient}}) =>
		queryClient.ensureQueryData(queries.queryProcessDefinitions({page: {limit: 1000}})),
	component: function ProcessesRoute() {
		return <Processes {...Route.useSearch()} />;
	},
});

export {Route};
