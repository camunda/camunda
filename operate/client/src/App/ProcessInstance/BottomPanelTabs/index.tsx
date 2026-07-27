/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Navigate, Outlet, useLocation, useNavigate} from 'react-router-dom';
import {useLayoutEffect, useRef} from 'react';
import {Paths} from 'modules/Routes';
import {Container, TabContent} from './styled';
import {TabListNav} from './TabListNav';
import {useProcessInstancePageParams} from '../useProcessInstancePageParams';
import {useCurrentPage} from 'modules/hooks/useCurrentPage';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useProcessInstanceIncidentsCount} from 'modules/queries/incidents/useProcessInstanceIncidentsCount';
import {useElementInstanceIncidentsCount} from 'modules/queries/incidents/useElementInstanceIncidentsCount';
import {useWaitStateStatistics} from 'modules/queries/waitStateStatistics/useWaitStateStatistics';
import {hasProcessLevelWaitState} from 'modules/utils/waitStates';
import {getClientConfig} from 'modules/utils/getClientConfig';
import {modificationsStore} from 'modules/stores/modifications';

function useSelectionAwareIncidentsCount(
  processInstanceKey: string,
  hasIncident: boolean,
) {
  const {resolvedElementInstance, isFetchingElement, selectedElementId} =
    useProcessInstanceElementSelection();
  const resolvedElementInstanceKey =
    resolvedElementInstance?.elementInstanceKey;

  const isElementInstanceSelected =
    resolvedElementInstanceKey !== undefined &&
    resolvedElementInstanceKey !== processInstanceKey;

  const {data: processIncidentsCount} = useProcessInstanceIncidentsCount(
    processInstanceKey ?? '',
    {
      enabled: hasIncident && !isElementInstanceSelected && !isFetchingElement,
      filter: {elementId: selectedElementId ?? undefined},
    },
  );
  const {data: elementIncidentsCount} = useElementInstanceIncidentsCount(
    resolvedElementInstanceKey ?? '',
    {
      enabled: hasIncident && isElementInstanceSelected,
    },
  );

  return isElementInstanceSelected
    ? elementIncidentsCount
    : processIncidentsCount;
}

const BottomPanelTabs: React.FC<{isHistoryTabVisible: boolean}> = ({
  isHistoryTabVisible,
}) => {
  const {hasSelection} = useProcessInstanceElementSelection();
  const {data: processInstance, isLoading: isProcessInstanceLoading} =
    useProcessInstance();
  const {data: waitStateStatistics, isLoading: isWaitStateLoading} =
    useWaitStateStatistics({
      enabled: getClientConfig().waitStatesEnabled,
    });
  const isProcessLevelWaiting = hasProcessLevelWaitState(
    waitStateStatistics,
    processInstance?.processDefinitionId,
  );
  const {processInstanceId} = useProcessInstancePageParams();
  const {currentPage} = useCurrentPage();
  const location = useLocation();
  const navigate = useNavigate();
  const hasIncident = processInstance?.hasIncident === true;

  const prevHasSelectionRef = useRef(hasSelection);
  useLayoutEffect(() => {
    // Switches to the default element tab when users
    // select an element without a previous selection.
    const prevHasSelection = prevHasSelectionRef.current;
    prevHasSelectionRef.current = hasSelection;
    if (
      !hasSelection ||
      prevHasSelection ||
      modificationsStore.isModificationModeEnabled
    ) {
      return;
    }

    const pathname = hasIncident
      ? Paths.processInstanceIncidents({processInstanceId})
      : Paths.processInstanceDetails({processInstanceId});
    navigate({pathname, search: location.search}, {replace: true});
  }, [hasSelection, hasIncident, processInstanceId, location.search, navigate]);

  const incidentsCount = useSelectionAwareIncidentsCount(
    processInstanceId ?? '',
    hasIncident,
  );

  const tabItems = [
    {
      label: 'Instance History',
      to: {
        pathname: Paths.processInstanceHistory({processInstanceId}),
      },
      key: 'instance-history',
      selected: currentPage === 'process-details-instance-history',
      title: 'Instance History',
      visible: isHistoryTabVisible,
    },
    {
      label: 'Incidents',
      to: {pathname: Paths.processInstanceIncidents({processInstanceId})},
      key: 'incidents',
      selected: currentPage === 'process-details-incidents',
      title: 'Incidents',
      visible:
        hasIncident && (incidentsCount === undefined || incidentsCount > 0),
      tagText: incidentsCount ?? 0,
    },
    {
      label: 'Details',
      to: {pathname: Paths.processInstanceDetails({processInstanceId})},
      key: 'details',
      selected: currentPage === 'process-details-details',
      title: 'Details',
      // Shown for a selected element instance, or for the process scope only
      // when there is a process-level wait state to surface.
      visible: hasSelection || isProcessLevelWaiting,
    },
    {
      label: 'Variables',
      to: {pathname: Paths.processInstanceVariables({processInstanceId})},
      key: 'variables',
      selected: currentPage === 'process-details-variables',
      title: 'Variables',
      visible: true,
    },
    {
      label: 'Input Mappings',
      to: {pathname: Paths.processInstanceInputMappings({processInstanceId})},
      key: 'input-mappings',
      selected: currentPage === 'process-details-input-mappings',
      title: 'Input Mappings',
      visible: hasSelection,
    },
    {
      label: 'Output Mappings',
      to: {
        pathname: Paths.processInstanceOutputMappings({processInstanceId}),
      },
      key: 'output-mappings',
      selected: currentPage === 'process-details-output-mappings',
      title: 'Output Mappings',
      visible: hasSelection,
    },
    {
      label: 'Listeners',
      to: {pathname: Paths.processInstanceListeners({processInstanceId})},
      key: 'listeners',
      selected: currentPage === 'process-details-listeners',
      title: 'Listeners',
      visible: true,
    },
    {
      label: 'Operations Log',
      to: {pathname: Paths.processInstanceOperationsLog({processInstanceId})},
      key: 'operations-log',
      selected: currentPage === 'process-details-operations-log',
      title: 'Operations Log',
      visible: true,
    },
  ] satisfies React.ComponentProps<typeof TabListNav>['items'];

  const selectedTab = tabItems.find((tab) => tab.selected);

  // Avoid redirecting away from the Details tab while it's still unknown
  // whether a process-level wait state exists (process instance or wait-state
  // query still loading) — otherwise a direct link to Details would bounce to
  // Variables before the wait state resolves.
  const isDeferringRedirect =
    selectedTab?.key === 'details' &&
    !hasSelection &&
    (isProcessInstanceLoading || isWaitStateLoading);

  return (
    <Container>
      <TabListNav label="Process Instance Bottom Panel Tabs" items={tabItems} />
      <TabContent>
        <Outlet />
      </TabContent>
      {selectedTab?.visible === false && !isDeferringRedirect && (
        <Navigate
          to={{
            ...location,
            pathname:
              hasSelection || isProcessLevelWaiting
                ? Paths.processInstanceDetails({processInstanceId})
                : Paths.processInstanceVariables({processInstanceId}),
          }}
          replace
        />
      )}
    </Container>
  );
};

export {BottomPanelTabs};
