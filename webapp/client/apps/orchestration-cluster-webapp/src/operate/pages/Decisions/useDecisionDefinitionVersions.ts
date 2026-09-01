/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {skipToken, useInfiniteQuery} from '@tanstack/react-query';
import {request} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';
import {mapQueryError} from '#/shared/http/mapQueryError';

const PAGE_LIMIT = 1000;

function useDecisionDefinitionVersions(decisionDefinitionId?: string, tenantId?: string) {
	const query = useInfiniteQuery({
		queryKey: ['decisionDefinitionVersions', decisionDefinitionId, tenantId] as const,
		queryFn:
			decisionDefinitionId === undefined
				? skipToken
				: async ({pageParam}) => {
						const {response, error} = await request(
							endpoints.queryDecisionDefinitions({
								filter: {decisionDefinitionId, tenantId},
								sort: [{field: 'version', order: 'desc'}],
								page: {after: pageParam, limit: PAGE_LIMIT},
							}),
						);
						if (error !== null) {
							throw mapQueryError(error);
						}

						return response.json();
					},
		initialPageParam: undefined as string | undefined,
		getNextPageParam: (lastPage, _pages, lastPageParam) => {
			const nextCursor = lastPage.page.endCursor ?? undefined;
			return lastPage.page.hasMoreTotalItems && nextCursor !== lastPageParam ? nextCursor : undefined;
		},
		select: ({pages}) => pages.flatMap((page) => page.items),
		staleTime: 'static',
	});

	const {fetchNextPage, hasNextPage, isFetchingNextPage, isFetchNextPageError} = query;
	useEffect(() => {
		if (hasNextPage && !isFetchingNextPage && !isFetchNextPageError) {
			void fetchNextPage();
		}
	}, [fetchNextPage, hasNextPage, isFetchingNextPage, isFetchNextPageError]);

	return query;
}

export {useDecisionDefinitionVersions};
