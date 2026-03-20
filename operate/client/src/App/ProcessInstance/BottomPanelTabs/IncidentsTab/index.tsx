/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {
  type Incident,
  type QueryIncidentsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.9';
import type {InfiniteData, UseInfiniteQueryResult} from '@tanstack/react-query';
import {
  useEnhancedIncidents,
  useIncidentsSort,
  type EnhancedIncident,
} from 'modules/hooks/incidents';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {useGetIncidentsByProcessInstancePaginated} from 'modules/queries/incidents/useGetIncidentsByProcessInstancePaginated';
import {useGetIncidentsByElementInstancePaginated} from 'modules/queries/incidents/useGetIncidentsByElementInstancePaginated';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';
import {getIncidentsSearchFilter, isListenerIncident} from 'modules/utils/incidents';
import {isInstanceRunning} from 'modules/utils/instance';
import {isGlobalListener} from 'modules/utils/listeners';
import {modificationsStore} from 'modules/stores/modifications';
import {IncidentsFilter} from './IncidentsFilter';
import {IncidentsTable} from './IncidentsTable';
import {PanelHeader} from 'modules/components/PanelHeader';
import {Container} from './styled';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';

const ROW_HEIGHT = 32;

const IncidentsTab: React.FC = observer(() => {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const {data: processInstance} = useProcessInstance();
  const {selectedElementId, resolvedElementInstance, isFetchingElement} =
    useProcessInstanceElementSelection();
  const resolvedElementInstanceKey =
    resolvedElementInstance?.elementInstanceKey;

  const enablePeriodicRefetch =
    processInstance !== undefined &&
    isInstanceRunning(processInstance) &&
    !modificationsStore.isModificationModeEnabled;

  const sort = useIncidentsSort();
  const filter = getIncidentsSearchFilter(
    incidentsPanelStore.state.selectedErrorTypes,
    selectedElementId ?? undefined,
  );

  const isElementInstanceSelected =
    resolvedElementInstanceKey !== undefined &&
    resolvedElementInstanceKey !== processInstanceId;

  const processInstanceResult = useGetIncidentsByProcessInstancePaginated(
    processInstanceId,
    {
      enabled:
        !isElementInstanceSelected &&
        processInstance !== undefined &&
        !isFetchingElement,
      enablePeriodicRefetch,
      payload: {sort, filter},
    },
  );

  const elementInstanceResult = useGetIncidentsByElementInstancePaginated(
    resolvedElementInstanceKey ?? '',
    {
      enabled: isElementInstanceSelected && processInstance !== undefined,
      enablePeriodicRefetch,
      payload: {
        sort,
        filter,
      },
    },
  );

  const result = isElementInstanceSelected
    ? elementInstanceResult
    : processInstanceResult;

  const {
    incidents,
    totalIncidentsCount,
    displayState,
    handleScrollStartReach,
    handleScrollEndReach,
  } = mapQueryResultToIncidentsHandle(result);

  const enhancedIncidents = useEnhancedIncidents(incidents);

  const filteredIncidents = filterBySource(
    enhancedIncidents,
    incidentsPanelStore.state.selectedSourceFilter,
  );

  return (
    <Container>
      <PanelHeader count={totalIncidentsCount} size="sm">
        <IncidentsFilter />
      </PanelHeader>
      <IncidentsTable
        state={displayState}
        onVerticalScrollStartReach={handleScrollStartReach}
        onVerticalScrollEndReach={handleScrollEndReach}
        processInstanceKey={processInstanceId}
        incidents={filteredIncidents}
      />
    </Container>
  );
});

function mapQueryResultToIncidentsHandle(
  result: UseInfiniteQueryResult<InfiniteData<QueryIncidentsResponseBody>>,
) {
  const incidents: Incident[] =
    result.data?.pages.flatMap((p) => p.items) ?? [];
  const totalIncidentsCount = result.data?.pages[0]?.page.totalItems ?? 0;
  const displayState = computeDisplayState(result, totalIncidentsCount);

  return {
    incidents,
    totalIncidentsCount,
    displayState,
    handleScrollStartReach: async (scrollDown: (amount: number) => void) => {
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

function computeDisplayState(
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

function filterBySource(
  incidents: EnhancedIncident[],
  sourceFilter: 'ALL' | 'GLOBAL' | 'MODEL',
): EnhancedIncident[] {
  if (sourceFilter === 'ALL') {
    return incidents;
  }
  return incidents.filter((incident) => {
    if (!isListenerIncident(incident)) {
      return false;
    }
    const global = isGlobalListener(incident.tags ?? []);
    return sourceFilter === 'GLOBAL' ? global : !global;
  });
}

export {IncidentsTab};
