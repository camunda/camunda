/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useGetIncidentsByElementInstancePaginated} from 'modules/queries/incidents/useGetIncidentsByElementInstancePaginated';
import {useGetIncidentsByProcessInstancePaginated} from 'modules/queries/incidents/useGetIncidentsByProcessInstancePaginated';
import {useEnhancedIncidents, useIncidentsSort} from 'modules/hooks/incidents';
import {getIncidentsSearchFilter} from 'modules/utils/incidents';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';
import {IncidentsTable} from '../../IncidentsWrapper/IncidentsTable';
import {IncidentsFilter} from '../../IncidentsWrapper/IncidentsFilter';
import {Content, FilterContainer} from './IncidentsContent.styled';
import {isInstanceRunning} from 'modules/utils/instance';
import {modificationsStore} from 'modules/stores/modifications';
import type {InfiniteData, UseInfiniteQueryResult} from '@tanstack/react-query';
import type {Incident} from '@camunda/camunda-api-zod-schemas/8.9';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';

const ROW_HEIGHT = 32;

function mapQueryResultToIncidentsHandle(
  result: UseInfiniteQueryResult<
    InfiniteData<{
      items: Incident[];
      page: {totalItems: number};
    }>
  >,
) {
  const incidents = result.data?.pages.flatMap((p) => p.items) ?? [];
  const totalIncidentsCount = result.data?.pages[0]?.page.totalItems ?? 0;
  const displayState = computeDisplayStateFromQueryResult(
    result,
    totalIncidentsCount,
  );

  return {
    incidents,
    totalIncidentsCount,
    displayState,
    handleScrollStartReach: async (scrollDown: (step: number) => void) => {
      if (result.hasPreviousPage && !result.isFetchingPreviousPage) {
        await result.fetchPreviousPage();
        const SMOOTH_SCROLL_STEP_SIZE = 5 * ROW_HEIGHT;
        scrollDown(SMOOTH_SCROLL_STEP_SIZE);
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
): React.ComponentProps<typeof IncidentsTable>['state'] {
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

const IncidentsContent: React.FC = observer(() => {
  const {data: processInstance} = useProcessInstance();
  const {
    hasSelection,
    selectedElementId,
    selectedElementInstanceKey,
    clearSelection,
  } = useProcessInstanceElementSelection();
  const isRootNodeSelected = !hasSelection;

  const enablePeriodicRefetch =
    processInstance &&
    isInstanceRunning(processInstance) &&
    !modificationsStore.isModificationModeEnabled;

  const sort = useIncidentsSort();
  const flowNodeId = selectedElementId;
  const isElementInstanceSelected =
    !!selectedElementInstanceKey &&
    selectedElementInstanceKey !== processInstance?.processInstanceKey;
  const isFlowNodeScoped = flowNodeId && !isRootNodeSelected;

  // For root node: show all process incidents with filters
  if (isRootNodeSelected && processInstance) {
    const filter = getIncidentsSearchFilter(
      incidentsPanelStore.state.selectedErrorTypes,
      incidentsPanelStore.state.selectedElementId ?? undefined,
    );

    const result = useGetIncidentsByProcessInstancePaginated(
      processInstance.processInstanceKey,
      {
        enabled: true,
        enablePeriodicRefetch,
        payload: {sort, filter},
      },
    );

    const handle = mapQueryResultToIncidentsHandle(result);
    const enhancedIncidents = useEnhancedIncidents(handle.incidents);

    return (
      <Content>
        <FilterContainer>
          <IncidentsFilter />
        </FilterContainer>
        <IncidentsTable
          state={handle.displayState}
          onVerticalScrollStartReach={handle.handleScrollStartReach}
          onVerticalScrollEndReach={handle.handleScrollEndReach}
          processInstanceKey={processInstance.processInstanceKey}
          incidents={enhancedIncidents}
        />
      </Content>
    );
  }

  // For selected flow node: show incidents filtered by flow node with filters
  // Show bar when we have a flowNodeId filter (not root, not specific element instance)
  if (isFlowNodeScoped && !isElementInstanceSelected && processInstance) {
    const filter = getIncidentsSearchFilter(
      incidentsPanelStore.state.selectedErrorTypes,
      flowNodeId,
    );

    const result = useGetIncidentsByProcessInstancePaginated(
      processInstance.processInstanceKey,
      {
        enabled: true,
        enablePeriodicRefetch,
        payload: {sort, filter},
      },
    );

    const handle = mapQueryResultToIncidentsHandle(result);
    const enhancedIncidents = useEnhancedIncidents(handle.incidents);
    return (
      <Content>
        <FilterContainer>
          <IncidentsFilter />
        </FilterContainer>
        <IncidentsTable
          state={handle.displayState}
          onVerticalScrollStartReach={handle.handleScrollStartReach}
          onVerticalScrollEndReach={handle.handleScrollEndReach}
          processInstanceKey={processInstance.processInstanceKey}
          incidents={enhancedIncidents}
        />
      </Content>
    );
  }

  // For selected element instance: show only that element's incidents without filters
  // But if it's scoped to a flow node, also show the bar
  if (isElementInstanceSelected && processInstance) {
    const filter = getIncidentsSearchFilter(
      incidentsPanelStore.state.selectedErrorTypes,
    );

    const result = useGetIncidentsByElementInstancePaginated(
      selectedElementInstanceKey,
      {
        enabled: true,
        enablePeriodicRefetch,
        payload: {sort, filter},
      },
    );

    const handle = mapQueryResultToIncidentsHandle(result);
    const enhancedIncidents = useEnhancedIncidents(handle.incidents);
    return (
      <Content>
        <IncidentsTable
          state={handle.displayState}
          onVerticalScrollStartReach={handle.handleScrollStartReach}
          onVerticalScrollEndReach={handle.handleScrollEndReach}
          processInstanceKey={processInstance.processInstanceKey}
          incidents={enhancedIncidents}
        />
      </Content>
    );
  }

  return null;
});

export {IncidentsContent};
