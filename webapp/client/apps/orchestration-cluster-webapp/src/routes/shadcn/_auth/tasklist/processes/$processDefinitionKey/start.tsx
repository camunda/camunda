/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback} from 'react';
import {createFileRoute, useNavigate, useRouter, type ErrorComponentProps} from '@tanstack/react-router';
import {useQueryClient, useSuspenseQuery} from '@tanstack/react-query';
import {queries} from '#/shared/http/queries';
import {requestErrorSchema} from '#/shared/http/request';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {
	StartProcessFormModal,
	StartProcessFormModalError,
	StartProcessFormModalSkeleton,
	type StartProcessFormModalErrorVariant,
} from '#/tasklist/modules/processes/shadcn.components/StartProcessFormModal';
import {useUploadDocuments} from '#/tasklist/modules/form-js/useUploadDocuments';
import {tryParseJSON} from '#/tasklist/modules/json/tryParseJSON';
import {ForbiddenError, ProcessStartFormImportError, ProcessStartFormNotFoundError} from '#/shared/errors';
import {useStartProcess} from '#/tasklist/modules/processes/useStartProcess';

const HTTP_STATUS_NOT_FOUND = 404;

function useCloseStartProcessForm() {
	const search = Route.useSearch();
	const navigate = useNavigate();

	return useCallback(() => navigate({to: '/shadcn/tasklist/processes', search}), [navigate, search]);
}

function getErrorVariant(error: unknown): StartProcessFormModalErrorVariant {
	if (error instanceof ProcessStartFormImportError) {
		return 'schema-import-failed';
	}

	if (error instanceof ProcessStartFormNotFoundError) {
		return 'not-found';
	}

	const result = requestErrorSchema.safeParse(error);

	if (result.success && result.data.response?.status === HTTP_STATUS_NOT_FOUND) {
		return 'not-found';
	}

	if (error instanceof ForbiddenError) {
		return 'forbidden';
	}

	return 'load-failed';
}

export const Route = createFileRoute('/shadcn/_auth/tasklist/processes/$processDefinitionKey/start')({
	loader: async ({context: {queryClient}, params: {processDefinitionKey}}) => {
		const process = await queryClient.ensureQueryData(queries.getProcessDefinition(processDefinitionKey));

		if (!process.hasStartForm) {
			throw new ProcessStartFormNotFoundError();
		}

		await queryClient.ensureQueryData(queries.getProcessStartForm(processDefinitionKey));
	},
	pendingComponent: function StartProcessFormPending() {
		const {processDefinitionKey} = Route.useParams();
		const close = useCloseStartProcessForm();

		return <StartProcessFormModalSkeleton processDisplayName={processDefinitionKey} onClose={close} />;
	},
	errorComponent: function StartProcessFormRouteError({error, reset}: ErrorComponentProps) {
		const {processDefinitionKey} = Route.useParams();
		const close = useCloseStartProcessForm();
		const router = useRouter();
		const queryClient = useQueryClient();
		const variant = getErrorVariant(error);
		const retry = useCallback(async () => {
			reset();
			await queryClient.resetQueries({
				queryKey: queries.getProcessStartForm(processDefinitionKey).queryKey,
				exact: true,
			});
			await router.invalidate();
		}, [processDefinitionKey, queryClient, reset, router]);

		return (
			<StartProcessFormModalError
				processDisplayName={processDefinitionKey}
				variant={variant}
				onClose={close}
				onRetry={variant === 'load-failed' ? retry : undefined}
			/>
		);
	},
	component: function StartProcessFormRoute() {
		const {processDefinitionKey} = Route.useParams();
		const {data: process} = useSuspenseQuery(queries.getProcessDefinition(processDefinitionKey));
		const {data: form} = useSuspenseQuery({
			...queries.getProcessStartForm(processDefinitionKey),
			refetchOnReconnect: false,
			refetchOnWindowFocus: false,
		});
		const {startProcess} = useStartProcess();
		const {mutateAsync: uploadDocuments} = useUploadDocuments();
		const close = useCloseStartProcessForm();
		const handleOnSubmit = useCallback(
			async (partialVariables: {name: string; value: string}[]) => {
				const variables = partialVariables.reduce<Record<string, unknown>>((result, {name, value}) => {
					result[name] = tryParseJSON(value);
					return result;
				}, {});

				startProcess(process, variables);
			},
			[process, startProcess],
		);

		return (
			<StartProcessFormModal
				processDisplayName={process.name ?? process.processDefinitionId}
				schema={form.schema}
				isMultiTenancyEnabled={getClientConfig().deployment.isMultiTenancyEnabled}
				tenantId={process.tenantId}
				onClose={close}
				onFileUpload={uploadDocuments}
				onSubmit={handleOnSubmit}
			/>
		);
	},
});
