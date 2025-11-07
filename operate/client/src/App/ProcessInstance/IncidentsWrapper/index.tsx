/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IncidentsOverlay} from './IncidentsOverlay';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';
import {Transition} from './styled';
import {IncidentsFilter} from './IncidentsFilter';
import {IncidentsTable} from './IncidentsTable';
import {IncidentsTable as IncidentsTableV2} from './IncidentsTable/v2';
import {PanelHeader} from 'modules/components/PanelHeader';
import {
  getFilteredIncidents,
  getIncidentsSearchFilter,
  init,
} from 'modules/utils/incidents';
import {
  useIncidents,
  useEnhancedIncidents,
  useIncidentsSort,
} from 'modules/hooks/incidents';
import {
  type Incident,
  type ProcessInstance,
  type QueryIncidentsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import type {InfiniteData, UseInfiniteQueryResult} from '@tanstack/react-query';
import {useEffect} from 'react';
import {IS_INCIDENTS_PANEL_V2} from 'modules/feature-flags';
import {isInstanceRunning} from 'modules/utils/instance';
import {modificationsStore} from 'modules/stores/modifications';
import {useGetIncidentsByProcessInstancePaginated} from 'modules/queries/incidents/useGetIncidentsByProcessInstancePaginated';
import {useGetIncidentsByElementInstancePaginated} from 'modules/queries/incidents/useGetIncidentsByElementInstancePaginated';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';

const ROW_HEIGHT = 32;

type Props = {
  processInstance: ProcessInstance;
  setIsInTransition: (isTransitionActive: boolean) => void;
};

const IncidentsWrapper: React.FC<Props> = observer(
  ({setIsInTransition, processInstance}) => {
    if (IS_INCIDENTS_PANEL_V2) {
      // Having a condition before hooks is usually not allowed but works this time,
      // because the condition is static during runtime.
      return (
        <IncidentsWrapperV2
          setIsInTransition={setIsInTransition}
          processInstance={processInstance}
        />
      );
    }

    const incidents = useIncidents();
    const filteredIncidents = getFilteredIncidents(incidents);

    useEffect(() => {
      init(processInstance);

      return () => {
        incidentsStore.reset();
      };
    }, [processInstance]);

    if (incidentsStore.incidentsCount === 0) {
      return null;
    }

    return (
      <>
        <Transition
          in={incidentsStore.state.isIncidentBarOpen}
          onEnter={() => setIsInTransition(true)}
          onEntered={() => setIsInTransition(false)}
          onExit={() => setIsInTransition(true)}
          onExited={() => setIsInTransition(false)}
          mountOnEnter
          unmountOnExit
          timeout={400}
        >
          <IncidentsOverlay>
            <PanelHeader
              title="Incidents View"
              count={filteredIncidents.length}
              size="sm"
            >
              <IncidentsFilter />
            </PanelHeader>
            <IncidentsTable />
          </IncidentsOverlay>
        </Transition>
      </>
    );
  },
);

const IncidentsWrapperV2: React.FC<Props> = observer(
  ({setIsInTransition, processInstance}) => {
    const enablePeriodicRefetch =
      isInstanceRunning(processInstance) &&
      !modificationsStore.isModificationModeEnabled;
    const isPanelVisible = incidentsPanelStore.state.isPanelVisible;
    const selectedElementInstance =
      incidentsPanelStore.state.selectedElementInstance;

    if (selectedElementInstance !== null) {
      return (
        <IncidentsByElementInstance
          elementInstanceKey={selectedElementInstance.elementInstanceKey}
          enablePeriodicRefetch={enablePeriodicRefetch}
          enableQuery={isPanelVisible}
        >
          {(handle) => (
            <IncidentsWrapperContent
              handle={handle}
              processInstanceKey={processInstance.processInstanceKey}
              setIsInTransition={setIsInTransition}
              isPanelVisible={isPanelVisible}
              selectedElementName={selectedElementInstance.elementName}
            />
          )}
        </IncidentsByElementInstance>
      );
    }

    return (
      <IncidentsByProcessInstance
        processInstanceKey={processInstance.processInstanceKey}
        enablePeriodicRefetch={enablePeriodicRefetch}
        enableQuery={isPanelVisible}
      >
        {(handle) => (
          <IncidentsWrapperContent
            handle={handle}
            processInstanceKey={processInstance.processInstanceKey}
            setIsInTransition={setIsInTransition}
            isPanelVisible={isPanelVisible}
          />
        )}
      </IncidentsByProcessInstance>
    );
  },
);

type IncidentsWrapperContentProps = {
  handle: IncidentsHandle;
  processInstanceKey: string;
  setIsInTransition: Props['setIsInTransition'];
  isPanelVisible: boolean;
  selectedElementName?: string;
};

const IncidentsWrapperContent: React.FC<IncidentsWrapperContentProps> = (
  props,
) => {
  const enhancedIncidents = useEnhancedIncidents(props.handle.incidents);
  const headerTitle = props.selectedElementName
    ? `Incidents - Filtered by "${props.selectedElementName}"`
    : 'Incidents';

  return (
    <Transition
      in={props.isPanelVisible}
      onEnter={() => props.setIsInTransition(true)}
      onEntered={() => props.setIsInTransition(false)}
      onExit={() => props.setIsInTransition(true)}
      onExited={() => props.setIsInTransition(false)}
      mountOnEnter
      unmountOnExit
      timeout={400}
    >
      <IncidentsOverlay>
        <PanelHeader
          title={headerTitle}
          count={props.handle.totalIncidentsCount}
          size="sm"
        >
          <IncidentsFilter />
        </PanelHeader>
        <IncidentsTableV2
          state={props.handle.displayState}
          onVerticalScrollStartReach={props.handle.handleScrollStartReach}
          onVerticalScrollEndReach={props.handle.handleScrollEndReach}
          processInstanceKey={props.processInstanceKey}
          incidents={enhancedIncidents}
        />
      </IncidentsOverlay>
    </Transition>
  );
};

type IncidentsSourceProps<Source> = Source & {
  enableQuery: boolean;
  enablePeriodicRefetch: boolean;
  children: (handle: IncidentsHandle) => React.ReactNode;
};

type IncidentsHandle = {
  incidents: Incident[];
  totalIncidentsCount: number;
  displayState: React.ComponentProps<typeof IncidentsTableV2>['state'];
  handleScrollStartReach: React.ComponentProps<
    typeof IncidentsTableV2
  >['onVerticalScrollStartReach'];
  handleScrollEndReach: React.ComponentProps<
    typeof IncidentsTableV2
  >['onVerticalScrollEndReach'];
};

const IncidentsByProcessInstance: React.FC<
  IncidentsSourceProps<{processInstanceKey: string}>
> = observer((props) => {
  const sort = useIncidentsSort();
  const filter = getIncidentsSearchFilter(
    incidentsPanelStore.state.selectedErrorTypes,
  );

  const result = useGetIncidentsByProcessInstancePaginated(
    props.processInstanceKey,
    {
      enabled: props.enableQuery,
      enablePeriodicRefetch: props.enablePeriodicRefetch,
      payload: {sort, filter},
    },
  );

  return props.children(mapQueryResultToIncidentsHandle(result));
});

const IncidentsByElementInstance: React.FC<
  IncidentsSourceProps<{elementInstanceKey: string}>
> = (props) => {
  const sort = useIncidentsSort();
  const filter = getIncidentsSearchFilter(
    incidentsPanelStore.state.selectedErrorTypes,
  );

  const result = useGetIncidentsByElementInstancePaginated(
    props.elementInstanceKey,
    {
      enabled: props.enableQuery,
      enablePeriodicRefetch: props.enablePeriodicRefetch,
      payload: {sort, filter},
    },
  );

  return props.children(mapQueryResultToIncidentsHandle(result));
};

function mapQueryResultToIncidentsHandle(
  result: UseInfiniteQueryResult<InfiniteData<QueryIncidentsResponseBody>>,
): IncidentsHandle {
  const incidents = result.data?.pages.flatMap((p) => p.items) ?? [];
  const totalIncidentsCount = result.data?.pages[0]?.page.totalItems ?? 0;

  let displayState: React.ComponentProps<typeof IncidentsTableV2>['state'] =
    'content';

  if (result.status === 'pending') {
    displayState = 'skeleton';
  } else if (
    result.isFetching &&
    !result.isRefetching &&
    !result.isFetchingPreviousPage &&
    !result.isFetchingNextPage
  ) {
    displayState = 'loading';
  } else if (result.status === 'error') {
    displayState = 'error';
  } else if (result.status === 'success' && totalIncidentsCount === 0) {
    displayState = 'empty';
  }

  return {
    incidents,
    totalIncidentsCount,
    displayState,
    handleScrollStartReach: async (scrollDown) => {
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

export {IncidentsWrapper, IncidentsWrapperV2};
