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
	MappingRule,
	QueryMappingRulesRequestBody,
	UpdateMappingRuleRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {getErrorMessage} from '#/shared/http/getErrorMessage';
import {endpoints} from '#/shared/http/endpoints';
import {queries} from '#/shared/http/queries';
import {isFirstPageWithRoom, patchListCaches, removeFromListCaches} from '#/shared/http/patchListCaches';
import {waitUntilGone, waitUntilReady} from '#/shared/http/waitUntilReady';
import {isDuplicateMappingRuleIdError} from './isDuplicateMappingRuleIdError';

const LIST_QUERY_KEY_PREFIX = ['queryMappingRules'];

function getMappingRuleId(mappingRule: Pick<MappingRule, 'mappingRuleId'>): string {
	return mappingRule.mappingRuleId;
}

function compareMappingRulesBySort(a: MappingRule, b: MappingRule, queryKey: QueryKey): number {
	const body = queryKey[1] as QueryMappingRulesRequestBody | undefined;
	const [sort] = body?.sort ?? [];
	if (sort === undefined) {
		return 0;
	}

	const valueA = a[sort.field];
	const valueB = b[sort.field];
	const direction = sort.order === 'DESC' ? -1 : 1;
	return direction * (valueA < valueB ? -1 : valueA > valueB ? 1 : 0);
}

function canInsertMappingRule(_mappingRule: MappingRule, queryKey: QueryKey, currentItemCount: number): boolean {
	const body = queryKey[1] as QueryMappingRulesRequestBody | undefined;
	const [sort] = body?.sort ?? [];
	const isUnfiltered = body?.filter?.mappingRuleId === undefined;
	return isUnfiltered && sort?.field !== 'name' && isFirstPageWithRoom(body?.page, currentItemCount);
}

function useMappingRuleMutations() {
	const {t} = useTranslation();
	const queryClient = useQueryClient();

	const invalidateMappingRules = () =>
		Promise.all([
			queryClient.invalidateQueries({queryKey: LIST_QUERY_KEY_PREFIX}),
			queryClient.invalidateQueries({queryKey: ['mappingRule']}),
		]);

	const reconcileMappingRules = (confirmed: boolean) => {
		if (confirmed) {
			void invalidateMappingRules();
		} else {
			toast.warning(t('admin.mappingRules.changeNotConfirmed'), {
				description: t('admin.mappingRules.changeNotConfirmedDescription'),
			});
		}
	};

	const create = useMutation({
		mutationFn: async (mappingRule: MappingRule) => {
			const {response, error} = await request(endpoints.createMappingRule(mappingRule));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json() as Promise<MappingRule>;
		},
		onSuccess: (mappingRule) => {
			patchListCaches(queryClient, {
				queryKeyPrefix: LIST_QUERY_KEY_PREFIX,
				item: mappingRule,
				getId: getMappingRuleId,
				compare: compareMappingRulesBySort,
				canInsert: canInsertMappingRule,
			});
			toast.success(t('admin.mappingRules.createMappingRuleSuccess'));

			void waitUntilReady(queryClient, queries.getMappingRule(mappingRule.mappingRuleId)).then((ready) =>
				reconcileMappingRules(ready !== undefined),
			);
		},
		onError: async (error) => {
			// A duplicate ID is corrected inline on the mapping rule ID field instead of a toast.
			if (!(await isDuplicateMappingRuleIdError(error))) {
				toast.error(t('admin.mappingRules.createMappingRuleError'), {description: await getErrorMessage(error)});
			}
		},
	});

	const update = useMutation({
		mutationFn: async (mappingRule: Pick<MappingRule, 'mappingRuleId'> & UpdateMappingRuleRequestBody) => {
			const {response, error} = await request(endpoints.updateMappingRule(mappingRule));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json() as Promise<MappingRule>;
		},
		onSuccess: (mappingRule) => {
			patchListCaches(queryClient, {
				queryKeyPrefix: LIST_QUERY_KEY_PREFIX,
				item: mappingRule,
				getId: getMappingRuleId,
				compare: compareMappingRulesBySort,
				canInsert: () => false,
			});
			queryClient.setQueryData(queries.getMappingRule(mappingRule.mappingRuleId).queryKey, mappingRule);
			toast.success(t('admin.mappingRules.updateMappingRuleSuccess'));

			void waitUntilReady(queryClient, queries.getMappingRule(mappingRule.mappingRuleId), {
				isReady: (data) =>
					data.name === mappingRule.name &&
					data.claimName === mappingRule.claimName &&
					data.claimValue === mappingRule.claimValue,
			}).then((ready) => reconcileMappingRules(ready !== undefined));
		},
		onError: async (error) => {
			toast.error(t('admin.mappingRules.updateMappingRuleError'), {description: await getErrorMessage(error)});
		},
	});

	const remove = useMutation({
		mutationFn: async (mappingRule: Pick<MappingRule, 'mappingRuleId'>) => {
			const {error} = await request(endpoints.deleteMappingRule(mappingRule));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return mappingRule.mappingRuleId;
		},
		onSuccess: (mappingRuleId) => {
			removeFromListCaches(queryClient, {
				queryKeyPrefix: LIST_QUERY_KEY_PREFIX,
				id: mappingRuleId,
				getId: getMappingRuleId,
			});
			queryClient.removeQueries({queryKey: queries.getMappingRule(mappingRuleId).queryKey});
			toast.success(t('admin.mappingRules.deleteMappingRuleSuccess'));

			void waitUntilGone(queryClient, {
				queryKey: ['mappingRuleGone', mappingRuleId],
				queryFn: async () => {
					const {response, error} = await request(endpoints.getMappingRule({mappingRuleId}));
					if (error !== null) {
						throw mapQueryError(error);
					}
					return response.json();
				},
			}).then(reconcileMappingRules);
		},
		onError: async (error) => {
			toast.error(t('admin.mappingRules.deleteMappingRuleError'), {description: await getErrorMessage(error)});
		},
	});

	return {create, update, remove};
}

export {useMappingRuleMutations};
