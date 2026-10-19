/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import c4FormJsCss from '@bpmn-io/c4-theme/assets/form-js.css?url';
import c4TokensCss from '@bpmn-io/c4-theme/assets/tokens.css?url';
import formJsCss from '@bpmn-io/form-js-viewer/dist/assets/form-js.css?url';
import {createFileRoute, notFound, Outlet, type ErrorComponentProps} from '@tanstack/react-router';
import {PageLayout} from '@camunda/design-system';
import {TasklistCasePage} from '#/tasklist/pages/TasklistCasePage';
import {loadCaseData, useCaseData} from '#/tasklist/modules/case-details/caseData';
import {ForbiddenError} from '#/shared/errors';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';
import {NotFoundPage} from '#/shared/pages/shadcn.components/NotFoundPage';

export const Route = createFileRoute('/_shadcn/_auth/tasklist/cases/$processInstanceKey')({
	head: () => ({
		links: [
			{rel: 'stylesheet', href: formJsCss},
			{rel: 'stylesheet', href: c4TokensCss},
			{rel: 'stylesheet', href: c4FormJsCss},
		],
	}),
	loader: async ({context: {queryClient}, params: {processInstanceKey}}) => {
		const routeData = await loadCaseData(queryClient, processInstanceKey);

		if (routeData === null) {
			throw notFound();
		}

		return routeData;
	},
	notFoundComponent: () => (
		<PageLayout>
			<NotFoundPage />
		</PageLayout>
	),
	errorComponent: function CaseErrorPage({error, reset}: ErrorComponentProps) {
		return (
			<PageLayout>
				{error instanceof ForbiddenError ? <ForbiddenPage /> : <GenericErrorPage reset={reset} />}
			</PageLayout>
		);
	},
	component: function TasklistCaseRoute() {
		const {processInstanceKey} = Route.useParams();
		const {caseFormSchema, variableNames} = Route.useLoaderData();
		const caseData = useCaseData(processInstanceKey, variableNames);

		return (
			<TasklistCasePage {...caseData} caseFormSchema={caseFormSchema}>
				<Outlet />
			</TasklistCasePage>
		);
	},
});
