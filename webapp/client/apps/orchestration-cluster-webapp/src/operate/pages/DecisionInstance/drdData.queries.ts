/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions, skipToken, useQuery} from '@tanstack/react-query';
import type {DecisionInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {request} from '#/shared/http/request';

type DrdData = Record<
	string,
	Pick<DecisionInstance, 'decisionDefinitionId' | 'decisionEvaluationInstanceKey' | 'state'>
>;

const PAGE_LIMIT = 1000;

function drdDataQuery(decisionEvaluationKey?: string) {
	return queryOptions({
		queryKey: ['decisionInstances', 'drdData', decisionEvaluationKey] as const,
		queryFn: !decisionEvaluationKey
			? skipToken
			: async (): Promise<DrdData> => {
					const drdData: DrdData = Object.create(null);
					let after: string | undefined;
					let fetched = 0;

					while (true) {
						const {response, error} = await request(
							endpoints.queryDecisionInstances({
								filter: {decisionEvaluationKey},
								sort: [{field: 'decisionEvaluationInstanceKey', order: 'asc'}],
								page: {after, limit: PAGE_LIMIT},
							}),
						);
						if (error !== null) {
							throw mapQueryError(error);
						}

						const {items, page} = await response.json();
						if (items.length === 0) {
							if (fetched < page.totalItems) {
								throw new Error(
									`Decision evaluation ${decisionEvaluationKey} returned an empty page after ${fetched} of ${page.totalItems} items`,
								);
							}
							break;
						}
						for (const instance of items) {
							const previous = drdData[instance.decisionDefinitionId];
							const prefix = `${decisionEvaluationKey}-`;
							const previousIndex = previous?.decisionEvaluationInstanceKey.startsWith(prefix)
								? Number(previous.decisionEvaluationInstanceKey.slice(prefix.length))
								: undefined;
							const nextIndex = instance.decisionEvaluationInstanceKey.startsWith(prefix)
								? Number(instance.decisionEvaluationInstanceKey.slice(prefix.length))
								: undefined;
							// RDBMS orders instance IDs lexically, so index 10 may arrive before index 2.
							if (
								previousIndex !== undefined &&
								nextIndex !== undefined &&
								Number.isSafeInteger(previousIndex) &&
								Number.isSafeInteger(nextIndex) &&
								previousIndex > 0 &&
								nextIndex > 0 &&
								previousIndex > nextIndex
							) {
								continue;
							}
							drdData[instance.decisionDefinitionId] = {
								decisionDefinitionId: instance.decisionDefinitionId,
								decisionEvaluationInstanceKey: instance.decisionEvaluationInstanceKey,
								state: instance.state,
							};
						}

						fetched += items.length;
						if (fetched >= page.totalItems && !page.hasMoreTotalItems) {
							break;
						}
						if (!page.endCursor || page.endCursor === after) {
							throw new Error(
								`Decision evaluation ${decisionEvaluationKey} cannot continue after ${fetched} items without a new cursor`,
							);
						}
						after = page.endCursor;
					}

					return drdData;
				},
	});
}

function useDrdData(decisionEvaluationKey?: string) {
	return useQuery(drdDataQuery(decisionEvaluationKey));
}

export {useDrdData};
export type {DrdData};
