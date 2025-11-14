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
} from '@camunda/camunda-api-zod-schemas/8.8';
import type {InfiniteData, UseInfiniteQueryResult} from '@tanstack/react-query';
import type {ProcessInstanceFilters} from 'modules/utils/filter/shared';
import {InstancesTable} from './index';

const ROW_HEIGHT = 34;
const SCROLL_STEP_SIZE = 5 * ROW_HEIGHT;

type InstancesTableWrapperProps = {
  filters: ProcessInstanceFilters;
  processDefinitionKeys?: string[];
  enablePeriodicRefetch: boolean;
};

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

const InstancesTableWrapper: React.FC<InstancesTableWrapperProps> = observer(
  (props) => {
    const result = useProcessInstancesPaginated({
      filters: props.filters,
      processDefinitionKeys: props.processDefinitionKeys,
      enablePeriodicRefetch: props.enablePeriodicRefetch,
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
  },
);

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
  switch (true) {
    case result.status === 'pending': {
      return 'skeleton';
    }
    case result.isFetching &&
      !result.isRefetching &&
      !result.isFetchingPreviousPage &&
      !result.isFetchingNextPage: {
      return 'loading';
    }
    case result.status === 'error': {
      return 'error';
    }
    case result.status === 'success' && totalItems === 0: {
      return 'empty';
    }
    default: {
      return 'content';
    }
  }
}

export {InstancesTableWrapper};
