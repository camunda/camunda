/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useProcessInstancesPaginated} from 'modules/queries/processInstance/useProcessInstancesPaginated';
import type {
  ProcessInstance,
  QueryProcessInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import type {InfiniteData, UseInfiniteQueryResult} from '@tanstack/react-query';
import {batchModificationStore} from 'modules/stores/batchModification';
import {InstancesTable} from './index';
import {
  useProcessInstancesSearchFilter,
  useProcessInstancesSearchSort,
} from 'modules/hooks/processInstancesSearch';
import {useSearchParams} from 'react-router-dom';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {processInstancesSelectionStore} from 'modules/stores/instancesSelection';
import {useEffect, useMemo} from 'react';

const ROW_HEIGHT = 34;
const SCROLL_STEP_SIZE = 5 * ROW_HEIGHT;

type ProcessInstancesHandle = {
  processInstances: ProcessInstance[];
  totalCount: number;
  hasMoreTotalItems: boolean;
  displayState: React.ComponentProps<typeof InstancesTable>['state'];
  handleScrollStartReach: React.ComponentProps<
    typeof InstancesTable
  >['onVerticalScrollStartReach'];
  handleScrollEndReach: React.ComponentProps<
    typeof InstancesTable
  >['onVerticalScrollEndReach'];
};

const InstancesTableWrapper: React.FC = observer(() => {
  const [searchParams] = useSearchParams();
  const hasActiveFilter = searchParams.get('active') === 'true';
  const hasIncidentsFilter = searchParams.get('incidents') === 'true';

  const variables = variableFilterStore.variables;
  const filter = useProcessInstancesSearchFilter(variables);
  const sort = useProcessInstancesSearchSort();

  const enablePeriodicRefetch =
    (hasActiveFilter || hasIncidentsFilter) &&
    !batchModificationStore.state.isEnabled;

  const result = useProcessInstancesPaginated({
    payload: {filter, sort},
    enablePeriodicRefetch,
    enabled: filter !== undefined,
  });

  const {
    processInstances,
    totalCount,
    hasMoreTotalItems,
    displayState,
    handleScrollStartReach,
    handleScrollEndReach,
  } = useMemo(() => mapQueryResultToProcessInstancesHandle(result), [result]);

  useEffect(() => {
    const visibleIds = processInstances.map(
      (instance) => instance.processInstanceKey,
    );
    const visibleRunningIds = processInstances
      .filter((instance) => instance.state === 'ACTIVE' || instance.hasIncident)
      .map((instance) => instance.processInstanceKey);

    const visibleFinishedIds = processInstances
      .filter(
        (instance) =>
          instance.state === 'COMPLETED' || instance.state === 'TERMINATED',
      )
      .map((instance) => instance.processInstanceKey);

    processInstancesSelectionStore.setRuntime({
      totalCount,
      visibleIds,
      visibleRunningIds,
      visibleFinishedIds,
    });
  }, [processInstances, totalCount]);

  return (
    <InstancesTable
      state={displayState}
      processInstances={processInstances}
      totalCount={totalCount}
      hasMoreTotalItems={hasMoreTotalItems}
      onVerticalScrollStartReach={handleScrollStartReach}
      onVerticalScrollEndReach={handleScrollEndReach}
    />
  );
});

function mapQueryResultToProcessInstancesHandle(
  result: UseInfiniteQueryResult<
    InfiniteData<QueryProcessInstancesResponseBody>
  >,
): ProcessInstancesHandle {
  const processInstances = result.data?.pages.flatMap((p) => p.items) ?? [];
  const totalCount = result.data?.pages[0]?.page.totalItems ?? 0;
  const hasMoreTotalItems =
    result.data?.pages[0]?.page.hasMoreTotalItems ?? false;
  const displayState = computeDisplayStateFromQueryResult(result, totalCount);

  return {
    processInstances,
    totalCount,
    hasMoreTotalItems,
    displayState,
    handleScrollStartReach: async (scrollDown) => {
      if (result.hasPreviousPage && !result.isFetchingPreviousPage) {
        await result.fetchPreviousPage();
        scrollDown(SCROLL_STEP_SIZE);
      }
    },
    handleScrollEndReach: () => {
      if (result.hasNextPage && !result.isFetchingNextPage) {
        result.fetchNextPage();
      }
    },
  };
}

function computeDisplayStateFromQueryResult(
  result: UseInfiniteQueryResult<InfiniteData<unknown>>,
  totalItems: number,
): React.ComponentProps<typeof InstancesTable>['state'] {
  if (result.status === 'error') {
    return 'error';
  }

  if (result.status === 'pending' && !result.data) {
    return result.fetchStatus === 'idle' ? 'empty' : 'skeleton';
  }

  if (
    result.isFetching &&
    !result.isFetchingPreviousPage &&
    !result.isFetchingNextPage &&
    result.isPlaceholderData
  ) {
    return 'loading';
  }

  if (result.status === 'success' && totalItems === 0) {
    return 'empty';
  }

  return 'content';
}

export {InstancesTableWrapper};
