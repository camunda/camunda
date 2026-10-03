/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import c4FormJsCss from '@bpmn-io/c4-theme/assets/form-js.css?url';
import c4TokensCss from '@bpmn-io/c4-theme/assets/tokens.css?url';
import formJsCss from '@bpmn-io/form-js-viewer/dist/assets/form-js.css?url';
import {createFileRoute, notFound, stripSearchParams, type ErrorComponentProps} from '@tanstack/react-router';
import {useSuspenseQuery, type QueryClient} from '@tanstack/react-query';
import {PageLayout} from '@camunda/design-system';
import type {FormResult, ProcessInstance, Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import {TasklistCaseDetailsPage} from '#/tasklist/pages/TasklistCaseDetailsPage';
import {parseCaseMetadata} from '#/tasklist/modules/case-details/caseMetadataSchema';
import {
	getCaseElementInstancesRequestBody,
	getCaseIncidentsRequestBody,
	getCaseUserTasksRequestBody,
	getCaseWaitStatesRequestBody,
	getDocumentVariablesRequestBody,
	getFormVariablesRequestBody,
	getMetadataVariableRequestBody,
} from '#/tasklist/modules/case-details/getCaseRequestBodies';
import {caseDetailsSearchDefaults, caseDetailsSearchSchema} from '#/tasklist/modules/case-details/searchSchema';
import {extractVariablesFromFormSchema} from '#/tasklist/modules/form-js/extractVariablesFromFormSchema';
import {ForbiddenError, TruncatedVariableError} from '#/shared/errors';
import {queries} from '#/shared/http/queries';
import {requestErrorSchema} from '#/shared/http/request';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';
import {NotFoundPage} from '#/shared/pages/shadcn.components/NotFoundPage';

const REFETCH_INTERVAL = 5000;

type LoaderData = {
	formSchema: string | null;
	formVariables: Variable[];
};

function isNotFoundError(error: unknown): boolean {
	const result = requestErrorSchema.safeParse(error);
	return result.success && result.data.response?.status === 404;
}

async function loadProcessInstance(queryClient: QueryClient, processInstanceKey: string): Promise<ProcessInstance> {
	try {
		return await queryClient.query(queries.getProcessInstance(processInstanceKey));
	} catch (error) {
		if (isNotFoundError(error)) {
			throw notFound();
		}

		throw error;
	}
}

async function loadCaseForm(
	queryClient: QueryClient,
	formId: string | undefined,
	tenantId: string,
): Promise<FormResult | null> {
	if (formId === undefined) {
		return null;
	}

	try {
		return await queryClient.query(queries.getLatestFormByFormId({formId, tenantId}));
	} catch (error) {
		if (isNotFoundError(error)) {
			return null;
		}

		throw error;
	}
}

async function loadFormVariables(
	queryClient: QueryClient,
	processInstanceKey: string,
	form: FormResult | null,
): Promise<Variable[]> {
	const names = form === null ? [] : extractVariablesFromFormSchema(form.schema);

	if (names.length === 0) {
		return [];
	}

	const {items} = await queryClient.query(
		queries.queryVariables(getFormVariablesRequestBody(processInstanceKey, names), {truncateValues: false}),
	);

	if (items.some(({isTruncated}) => isTruncated)) {
		throw new TruncatedVariableError();
	}

	return items;
}

export const Route = createFileRoute('/_shadcn/_auth/tasklist/cases/$processInstanceKey')({
	validateSearch: caseDetailsSearchSchema,
	search: {
		middlewares: [stripSearchParams(caseDetailsSearchDefaults)],
	},
	head: () => ({
		links: [
			{rel: 'stylesheet', href: formJsCss},
			{rel: 'stylesheet', href: c4TokensCss},
			{rel: 'stylesheet', href: c4FormJsCss},
		],
	}),
	loader: async ({context: {queryClient}, params: {processInstanceKey}}): Promise<LoaderData> => {
		const [processInstance, metadataVariables] = await Promise.all([
			loadProcessInstance(queryClient, processInstanceKey),
			queryClient.query(
				queries.queryVariables(getMetadataVariableRequestBody(processInstanceKey), {truncateValues: false}),
			),
		]);
		const metadata = parseCaseMetadata(metadataVariables.items[0]);
		const [form] = await Promise.all([
			loadCaseForm(queryClient, metadata?.formId, processInstance.tenantId),
			queryClient.query(queries.getCurrentUser()),
			queryClient.query(queries.getProcessDefinitionXml(processInstance.processDefinitionKey)),
			queryClient.query(queries.getProcessInstanceElementStatistics(processInstanceKey)),
			queryClient.query(queries.queryElementInstances(getCaseElementInstancesRequestBody(processInstanceKey))),
			queryClient.query(queries.queryElementInstanceWaitStates(getCaseWaitStatesRequestBody(processInstanceKey))),
			queryClient.query(queries.queryUserTasksPage(getCaseUserTasksRequestBody(processInstance))),
			queryClient.query(queries.queryProcessInstanceIncidents(processInstanceKey, getCaseIncidentsRequestBody())),
			queryClient.query(
				queries.queryVariables(getDocumentVariablesRequestBody(processInstanceKey), {truncateValues: false}),
			),
		]);

		return {
			formSchema: form?.schema ?? null,
			formVariables: await loadFormVariables(queryClient, processInstanceKey, form),
		};
	},
	notFoundComponent: () => (
		<PageLayout>
			<NotFoundPage />
		</PageLayout>
	),
	errorComponent: function TasklistCaseDetailsErrorPage({error, reset}: ErrorComponentProps) {
		if (error instanceof ForbiddenError) {
			return (
				<PageLayout>
					<ForbiddenPage />
				</PageLayout>
			);
		}

		return (
			<PageLayout>
				<GenericErrorPage reset={reset} />
			</PageLayout>
		);
	},
	component: function TasklistCaseDetailsRoute() {
		const {processInstanceKey} = Route.useParams();
		const {progressView} = Route.useSearch();
		const {formSchema, formVariables} = Route.useLoaderData();
		const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());
		const {data: processInstance} = useSuspenseQuery({
			...queries.getProcessInstance(processInstanceKey),
			refetchInterval: REFETCH_INTERVAL,
		});
		const {data: metadataVariables} = useSuspenseQuery({
			...queries.queryVariables(getMetadataVariableRequestBody(processInstanceKey), {truncateValues: false}),
			refetchInterval: REFETCH_INTERVAL,
		});
		const {data: xml} = useSuspenseQuery(queries.getProcessDefinitionXml(processInstance.processDefinitionKey));
		const {data: statistics} = useSuspenseQuery({
			...queries.getProcessInstanceElementStatistics(processInstanceKey),
			refetchInterval: REFETCH_INTERVAL,
		});
		const {data: elementInstances} = useSuspenseQuery({
			...queries.queryElementInstances(getCaseElementInstancesRequestBody(processInstanceKey)),
			refetchInterval: REFETCH_INTERVAL,
		});
		const {data: waitStates} = useSuspenseQuery({
			...queries.queryElementInstanceWaitStates(getCaseWaitStatesRequestBody(processInstanceKey)),
			refetchInterval: REFETCH_INTERVAL,
		});
		const {data: userTasks} = useSuspenseQuery({
			...queries.queryUserTasksPage(getCaseUserTasksRequestBody(processInstance)),
			refetchInterval: REFETCH_INTERVAL,
		});
		const {data: incidents} = useSuspenseQuery({
			...queries.queryProcessInstanceIncidents(processInstanceKey, getCaseIncidentsRequestBody()),
			refetchInterval: REFETCH_INTERVAL,
		});
		const {data: documentVariables} = useSuspenseQuery({
			...queries.queryVariables(getDocumentVariablesRequestBody(processInstanceKey), {truncateValues: false}),
			refetchInterval: REFETCH_INTERVAL,
		});
		const metadata = useMemo(() => parseCaseMetadata(metadataVariables.items[0]), [metadataVariables.items]);
		const caseUserTasks = useMemo(
			() =>
				userTasks.items.filter(
					(userTask) => (userTask.rootProcessInstanceKey ?? userTask.processInstanceKey) === processInstanceKey,
				),
			[userTasks.items, processInstanceKey],
		);

		return (
			<TasklistCaseDetailsPage
				processInstance={processInstance}
				metadata={metadata}
				xml={xml}
				statistics={statistics.items}
				elementInstances={elementInstances.items}
				waitStates={waitStates.items}
				userTasks={caseUserTasks}
				incidents={incidents.items}
				documentVariables={documentVariables.items}
				formSchema={formSchema}
				formVariables={formVariables}
				currentUsername={currentUser.username}
				progressView={progressView}
			/>
		);
	},
});
