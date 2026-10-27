/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {useInfiniteQuery} from '@tanstack/react-query';
import {useProcessInstancePage} from './useProcessInstancePage';
import {useVariableScope} from './useVariableScope';
import {variablesQuery} from './variables.queries';

function useScopeVariableNames(scopeKey: string | null) {
	const {processInstanceId} = useProcessInstancePage();
	const {canQuery} = useVariableScope();
	const {data, error, isSuccess, hasNextPage, isFetching, fetchNextPage} = useInfiniteQuery({
		...variablesQuery({processInstanceKey: processInstanceId, scopeKey: canQuery ? scopeKey : null}),
		select: ({pages}) => pages.flatMap(({items}) => items.map(({name}) => name)),
	});

	useEffect(() => {
		if (hasNextPage && !isFetching && error === null) {
			void fetchNextPage({cancelRefetch: false});
		}
	}, [hasNextPage, isFetching, fetchNextPage, error]);

	return {names: data ?? [], isComplete: !canQuery || (isSuccess && !hasNextPage)};
}

export {useScopeVariableNames};
