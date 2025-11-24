/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useMemo} from 'react';
import {useProcessInstancesPaginated} from 'modules/queries/processInstance/useProcessInstancesPaginated';
import type {
  ProcessInstance,
  QueryProcessInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import type {InfiniteData, UseInfiniteQueryResult} from '@tanstack/react-query';
import {useFilters} from 'modules/hooks/useFilters';
import {processesStore} from 'modules/stores/processes/processes.list';
import {batchModificationStore} from 'modules/stores/batchModification';
import {InstancesTable} from './index';
import {parseProcessInstancesSearchSort} from 'modules/utils/filter/v2/processInstancesSearchSort';
import {useSearchParams} from 'react-router-dom';

const ROW_HEIGHT = 34;
const SCROLL_STEP_SIZE = 5 * ROW_HEIGHT;

type ProcessInstancesHandle = {
  processInstances: ProcessInstance[];
  totalProcessInstancesCount: number;
  displayState: React.ComponentProps<typeof InstancesTable>['state'];
  handleScrollStartReach: React.ComponentProps<
    typeof InstancesTable
  >['onVerticalScrollStartReach'];
  handleScrollEndReach: React.ComponentProps<
    typeof InstancesTable
  >['onVerticalScrollEndReach'];
};

const InstancesTableWrapper: React.FC = observer(() => {
  const [params] = useSearchParams();
  const {getFilters} = useFilters();
  const filters = getFilters();

  const {process, tenant, version, active, incidents} = filters;
  const sort = parseProcessInstancesSearchSort(params);

  const processDefinitionKey = processesStore.getProcessId({
    process,
    tenant,
    version,
  });

  const processDefinitionKeys = useMemo(() => {
    if (processDefinitionKey) {
      return [processDefinitionKey];
    }
    return undefined;
  }, [processDefinitionKey]);

  const enablePeriodicRefetch =
    (active === true || incidents === true) &&
    !batchModificationStore.state.isEnabled;

  const result = useProcessInstancesPaginated({
    filters,
    processDefinitionKeys,
    enablePeriodicRefetch,
    sort,
    enabled: true,
  });

  const handle = mapQueryResultToProcessInstancesHandle(result);

  return (
    <InstancesTable
      state={handle.displayState}
      processInstances={handle.processInstances}
      totalProcessInstancesCount={handle.totalProcessInstancesCount}
      onVerticalScrollStartReach={handle.handleScrollStartReach}
      onVerticalScrollEndReach={handle.handleScrollEndReach}
    />
  );
});

function mapQueryResultToProcessInstancesHandle(
  result: UseInfiniteQueryResult<
    InfiniteData<QueryProcessInstancesResponseBody>
  >,
): ProcessInstancesHandle {
  const processInstances = result.data?.pages.flatMap((p) => p.items) ?? [];
  const totalProcessInstancesCount =
    result.data?.pages[0]?.page.totalItems ?? 0;
  const displayState = computeDisplayStateFromQueryResult(
    result,
    totalProcessInstancesCount,
  );

  return {
    processInstances,
    totalProcessInstancesCount,
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
    return 'skeleton';
  }

  if (
    result.isFetching &&
    !result.isFetchingPreviousPage &&
    !result.isFetchingNextPage
  ) {
    return 'loading';
  }

  if (result.status === 'success' && totalItems === 0) {
    return 'empty';
  }

  return 'content';
}

export {InstancesTableWrapper};
