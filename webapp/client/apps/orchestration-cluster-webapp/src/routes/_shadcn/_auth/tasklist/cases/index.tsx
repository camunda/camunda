/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback} from 'react';
import {createFileRoute, stripSearchParams, useNavigate} from '@tanstack/react-router';
import {TasklistCasesPage} from '#/tasklist/pages/TasklistCasesPage';
import {casesSearchDefaults, casesSearchSchema, type CasesSearch} from '#/tasklist/modules/cases/searchSchema';

const Route = createFileRoute('/_shadcn/_auth/tasklist/cases/')({
	validateSearch: casesSearchSchema,
	search: {
		middlewares: [stripSearchParams(casesSearchDefaults)],
	},
	component: function TasklistCasesRoute() {
		const search = Route.useSearch();
		const navigate = useNavigate();

		const handleSearchChange = useCallback(
			(next: Partial<CasesSearch>) => {
				navigate({to: '/tasklist/cases', search: {...search, ...next}});
			},
			[navigate, search],
		);

		return <TasklistCasesPage cases={[]} totalItems={0} search={search} onSearchChange={handleSearchChange} />;
	},
});

export {Route};
