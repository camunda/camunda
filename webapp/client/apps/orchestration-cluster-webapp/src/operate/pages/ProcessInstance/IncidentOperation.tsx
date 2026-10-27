/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useIsMutating, useMutation, useQueryClient, type QueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {InlineLoading} from '@carbon/react';
import type {Incident} from '@camunda/camunda-api-zod-schemas/8.11';
import {OperationItems} from '#/operate/shared/OperationItems/OperationItems';
import {OperationItem} from '#/operate/shared/OperationItem/OperationItem';
import {handleOperationError} from '#/operate/shared/utils/handleOperationError';
import {endpoints} from '#/shared/http/endpoints';
import {request, requestErrorSchema} from '#/shared/http/request';
import {PROCESS_INSTANCE_INCIDENTS_KEY} from './incidents.queries';
import {PROCESS_INSTANCE_INCIDENTS_COUNT_KEY, SELECTION_INCIDENTS_COUNT_KEY} from './processInstance.queries';
import {OperationContainer} from './incidents.styled';

type Props = {
	incidentKey: string;
	jobKey: string | null;
	disabled: boolean;
};

const MAX_CONFIRMATION_ATTEMPTS = 10;

function isClientError(error: unknown) {
	const status = requestErrorSchema.safeParse(error).data?.response?.status;
	return status !== undefined && status >= 400 && status < 500;
}

async function send(input: Request) {
	const {response, error} = await request(input);
	if (error !== null) {
		throw error;
	}
	return response;
}

function waitUntilIncidentResolved(queryClient: QueryClient, incidentKey: string) {
	return queryClient.fetchQuery({
		queryKey: ['incident', incidentKey],
		queryFn: async () => {
			const incident: Incident = await (await send(endpoints.getIncident(incidentKey))).json();
			if (incident.state === 'ACTIVE') {
				throw new Error('Incident is still active');
			}
			return incident;
		},
		retry: (failureCount, error) => failureCount < MAX_CONFIRMATION_ATTEMPTS && !isClientError(error),
	});
}

const IncidentOperation: React.FC<Props> = ({incidentKey, jobKey, disabled}) => {
	const {t} = useTranslation();
	const queryClient = useQueryClient();
	const mutationKey = ['resolveIncident', incidentKey];
	const isRetryPending = useIsMutating({mutationKey, exact: true}) > 0;
	const {mutate} = useMutation({
		mutationKey,
		scope: {id: incidentKey},
		mutationFn: async () => {
			if (jobKey !== null) {
				await send(endpoints.updateJob(jobKey, {changeset: {retries: 1}}));
			}
			await send(endpoints.resolveIncident(incidentKey));
			await waitUntilIncidentResolved(queryClient, incidentKey);
		},
		onSettled: () =>
			Promise.all(
				[PROCESS_INSTANCE_INCIDENTS_KEY, PROCESS_INSTANCE_INCIDENTS_COUNT_KEY, SELECTION_INCIDENTS_COUNT_KEY].map(
					(key) => queryClient.invalidateQueries({queryKey: [key]}),
				),
			),
		onError: (error) => handleOperationError(requestErrorSchema.safeParse(error).data?.response?.status),
	});

	return (
		<OperationContainer orientation="horizontal">
			{isRetryPending && <InlineLoading data-testid="operation-spinner" />}
			<OperationItems>
				<OperationItem
					type="RESOLVE_INCIDENT"
					onClick={(event) => {
						event.stopPropagation();
						mutate();
					}}
					title={t('operate.processInstance.incidents.retryIncident')}
					disabled={isRetryPending || disabled}
					size="sm"
				/>
			</OperationItems>
		</OperationContainer>
	);
};

export {IncidentOperation};
