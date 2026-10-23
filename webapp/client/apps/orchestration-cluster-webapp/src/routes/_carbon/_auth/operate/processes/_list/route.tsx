/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, Outlet} from '@tanstack/react-router';
import {t} from 'i18next';
import {Processes} from '#/operate/pages/Processes/Processes';
import {processesSearchSchema, stripLegacyProcessFilters} from '#/operate/pages/Processes/processesFilter';
import {latestDefinitionsQuery} from '#/operate/shared/queries/processDefinitions.queries';
import {isSpecificTenant} from '#/operate/shared/utils/isSpecificTenant';

const Route = createFileRoute('/_carbon/_auth/operate/processes/_list')({
	validateSearch: processesSearchSchema,
	search: {middlewares: [stripLegacyProcessFilters]},
	loaderDeps: ({search: {tenantId}}) => ({tenantId: isSpecificTenant(tenantId) ? tenantId : undefined}),
	loader: ({context: {queryClient}, deps: {tenantId}}) => queryClient.ensureQueryData(latestDefinitionsQuery(tenantId)),
	head: () => ({meta: [{title: t('operate.processes.pageTitle')}]}),
	component: function ProcessesRoute() {
		return (
			<>
				<Processes {...Route.useSearch()} />
				<Outlet />
			</>
		);
	},
});

export {Route};
