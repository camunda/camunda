/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient, type QueryKey} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {toast} from '@camunda/design-system';
import type {
	ClusterVariable,
	ClusterVariableSearchResult,
	QueryClusterVariablesRequestBody,
	UpdateClusterVariableRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {getErrorMessage} from '#/shared/http/getErrorMessage';
import {endpoints} from '#/shared/http/endpoints';
import {queries} from '#/shared/http/queries';
import {isFirstPageWithRoom, patchListCaches, removeFromListCaches} from '#/shared/http/patchListCaches';
import {waitUntilReady} from '#/shared/http/waitUntilReady';
import {isDuplicateClusterVariableError} from './isDuplicateClusterVariableError';

type ClusterVariableIdentity = Pick<ClusterVariable, 'name' | 'scope' | 'tenantId'>;
type ClusterVariableInput = ClusterVariableIdentity & Pick<UpdateClusterVariableRequestBody, 'value' | 'metadata'>;

const LIST_QUERY_KEY_PREFIX = ['queryClusterVariables'];

function getClusterVariableId({name, scope, tenantId}: ClusterVariableIdentity): string {
	return `${scope}:${tenantId ?? ''}:${name}`;
}

function toIdentity({name, scope, tenantId}: ClusterVariableIdentity): ClusterVariableIdentity {
	return {name, scope, tenantId};
}

function compareClusterVariablesByName(
	a: ClusterVariableSearchResult,
	b: ClusterVariableSearchResult,
	queryKey: QueryKey,
): number {
	const body = queryKey[1] as QueryClusterVariablesRequestBody | undefined;
	const direction = body?.sort?.[0]?.order === 'DESC' ? -1 : 1;
	return direction * (a.name < b.name ? -1 : a.name > b.name ? 1 : 0);
}

function clusterVariableMatchesFilter(
	clusterVariable: ClusterVariableIdentity,
	filter: QueryClusterVariablesRequestBody['filter'],
): boolean {
	const nameFilter = filter?.name;
	if (nameFilter === undefined) {
		return true;
	}
	if (typeof nameFilter === 'string') {
		return clusterVariable.name === nameFilter;
	}

	const pattern = nameFilter.$like;
	return pattern === undefined || clusterVariable.name.includes(pattern.replaceAll('*', ''));
}

function canInsertClusterVariable(
	clusterVariable: ClusterVariableSearchResult,
	queryKey: QueryKey,
	currentItemCount: number,
): boolean {
	const body = queryKey[1] as QueryClusterVariablesRequestBody | undefined;
	return (
		clusterVariableMatchesFilter(clusterVariable, body?.filter) && isFirstPageWithRoom(body?.page, currentItemCount)
	);
}

function useClusterVariableMutations() {
	const {t} = useTranslation();
	const queryClient = useQueryClient();

	const searchClusterVariable = ({name, scope, tenantId}: ClusterVariableIdentity) =>
		queries.queryClusterVariables(
			{filter: {name, scope, ...(typeof tenantId === 'string' && {tenantId})}, page: {limit: 1}},
			{truncateValues: false},
		);

	const invalidateClusterVariables = () =>
		Promise.all([
			queryClient.invalidateQueries({queryKey: LIST_QUERY_KEY_PREFIX}),
			queryClient.invalidateQueries({queryKey: ['getClusterVariable']}),
		]);

	const reconcileClusterVariables = (confirmed: boolean) => {
		if (confirmed) {
			void invalidateClusterVariables();
		} else {
			toast.warning(t('admin.clusterVariables.changeNotConfirmed'), {
				description: t('admin.clusterVariables.changeNotConfirmedDescription'),
			});
		}
	};

	const patchLists = (clusterVariable: ClusterVariable) =>
		patchListCaches(queryClient, {
			queryKeyPrefix: LIST_QUERY_KEY_PREFIX,
			item: {...clusterVariable, isTruncated: false} satisfies ClusterVariableSearchResult,
			getId: getClusterVariableId,
			compare: compareClusterVariablesByName,
			canInsert: canInsertClusterVariable,
		});

	const create = useMutation({
		mutationFn: async (clusterVariable: ClusterVariableInput) => {
			const {response, error} = await request(endpoints.createClusterVariable(clusterVariable));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json() as Promise<ClusterVariable>;
		},
		onSuccess: (clusterVariable) => {
			patchLists(clusterVariable);
			toast.success(t('admin.clusterVariables.createClusterVariableSuccess'));

			void waitUntilReady(queryClient, searchClusterVariable(clusterVariable), {
				isReady: ({items}) =>
					items.some((item) => getClusterVariableId(item) === getClusterVariableId(clusterVariable)),
			}).then((ready) => reconcileClusterVariables(ready !== undefined));
		},
		onError: async (error) => {
			// A duplicate name is corrected inline on the name field instead of a toast.
			if (!(await isDuplicateClusterVariableError(error))) {
				toast.error(t('admin.clusterVariables.createClusterVariableError'), {
					description: await getErrorMessage(error),
				});
			}
		},
	});

	const update = useMutation({
		mutationFn: async (clusterVariable: ClusterVariableInput) => {
			const {response, error} = await request(endpoints.updateClusterVariable(clusterVariable));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json() as Promise<ClusterVariable>;
		},
		onSuccess: (clusterVariable) => {
			patchLists(clusterVariable);
			toast.success(t('admin.clusterVariables.updateClusterVariableSuccess'));

			void waitUntilReady(queryClient, searchClusterVariable(clusterVariable), {
				isReady: ({items}) =>
					items.some(
						(item) =>
							getClusterVariableId(item) === getClusterVariableId(clusterVariable) &&
							item.value === clusterVariable.value,
					),
			}).then((ready) => reconcileClusterVariables(ready !== undefined));
		},
		onError: async (error) => {
			toast.error(t('admin.clusterVariables.updateClusterVariableError'), {description: await getErrorMessage(error)});
		},
	});

	const remove = useMutation({
		mutationFn: async (clusterVariable: ClusterVariableIdentity) => {
			const {error} = await request(endpoints.deleteClusterVariable(clusterVariable));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return clusterVariable;
		},
		onSuccess: (clusterVariable) => {
			const identity = toIdentity(clusterVariable);
			removeFromListCaches(queryClient, {
				queryKeyPrefix: LIST_QUERY_KEY_PREFIX,
				id: getClusterVariableId(identity),
				getId: getClusterVariableId,
			});
			queryClient.removeQueries({queryKey: queries.getClusterVariable(identity).queryKey});
			toast.success(t('admin.clusterVariables.deleteClusterVariableSuccess'));

			void waitUntilReady(queryClient, searchClusterVariable(identity), {
				isReady: ({items}) => items.every((item) => getClusterVariableId(item) !== getClusterVariableId(identity)),
			}).then((ready) => reconcileClusterVariables(ready !== undefined));
		},
		onError: async (error) => {
			toast.error(t('admin.clusterVariables.deleteClusterVariableError'), {description: await getErrorMessage(error)});
		},
	});

	return {create, update, remove};
}

export {useClusterVariableMutations};
