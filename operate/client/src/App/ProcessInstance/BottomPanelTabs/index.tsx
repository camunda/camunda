/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {Navigate, Outlet, useLocation, useNavigate} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {Container, TabContent} from './styled';
import {TabListNav} from './TabListNav';
import {useProcessInstancePageParams} from '../useProcessInstancePageParams';
import {useCurrentPage} from 'modules/hooks/useCurrentPage';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useProcessInstanceIncidentsCount} from 'modules/queries/incidents/useProcessInstanceIncidentsCount';
import {useElementInstanceIncidentsCount} from 'modules/queries/incidents/useElementInstanceIncidentsCount';
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
  const {hasSelection, selectedElementInstanceKey} =
    useProcessInstanceElementSelection();
  const {data: processInstance} = useProcessInstance();
  const {processInstanceId} = useProcessInstancePageParams();
  const {currentPage} = useCurrentPage();
  const location = useLocation();
  const navigate = useNavigate();
  const hasIncident = processInstance?.hasIncident === true;
  const incidentsCount = useSelectionAwareIncidentsCount(
    processInstanceId ?? '',
    hasIncident,
  );

  // When the user selects an element on the diagram (transitioning from no
  // selection to having one), default to the Details tab so the selection
  // actually has somewhere to land. Don't fight subsequent manual tab
  // switches — only steer on the edge from deselected → selected.
  const prevHasSelection = useRef(hasSelection);
  useEffect(() => {
    if (
      hasSelection &&
      !prevHasSelection.current &&
      currentPage !== 'process-details-details' &&
      processInstanceId
    ) {
      navigate(
        {
          pathname: Paths.processInstanceDetails({processInstanceId}),
          search: location.search,
        },
        {replace: true},
      );
    }
    prevHasSelection.current = hasSelection;
  }, [hasSelection, currentPage, processInstanceId, location.search, navigate]);

  const tabItems = [
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
      visible: hasSelection && selectedElementInstanceKey !== processInstanceId,
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
  ] satisfies React.ComponentProps<typeof TabListNav>['items'];

  const selectedTab = tabItems.find((tab) => tab.selected);

  return (
    <Container>
      <TabListNav label="Process Instance Bottom Panel Tabs" items={tabItems} />
      <TabContent>
        <Outlet />
      </TabContent>
      {selectedTab?.visible === false && (
        <Navigate
          to={{
            ...location,
            pathname: Paths.processInstanceVariables({processInstanceId}),
          }}
          replace
        />
      )}
    </Container>
  );
};

export {BottomPanelTabs};
