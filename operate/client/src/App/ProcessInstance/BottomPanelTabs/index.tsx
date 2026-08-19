/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLayoutEffect, useRef} from 'react';
import {Navigate, Outlet, useLocation, useNavigate} from 'react-router';
import {Paths} from 'modules/Routes';
import {Container, TabContent} from './styled';
import {TabListNav} from './TabListNav';
import {useProcessInstancePageParams} from '../useProcessInstancePageParams';
import {useCurrentPage} from 'modules/hooks/useCurrentPage';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useProcessInstanceIncidentsCount} from 'modules/queries/incidents/useProcessInstanceIncidentsCount';
import {useElementInstanceIncidentsCount} from 'modules/queries/incidents/useElementInstanceIncidentsCount';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
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

const BottomPanelTabs: React.FC = () => {
  const {hasSelection, selectedElementId} =
    useProcessInstanceElementSelection();
  const {data: processInstance} = useProcessInstance();
  const {data: businessObjects} = useBusinessObjects();
  const {processInstanceId} = useProcessInstancePageParams();
  const {currentPage} = useCurrentPage();
  const location = useLocation();
  const navigate = useNavigate();
  const hasIncident = processInstance?.hasIncident === true;

  const prevHasSelectionRef = useRef(hasSelection);
  useLayoutEffect(() => {
    // Switches to the Details tab when users select a call activity, so the
    // "Called Process Instance" link is immediately visible - independent of
    // the general any-element selection behavior.
    //
    // Business objects load asynchronously, so a selection can arrive before
    // we can classify its element type. Bail out without touching
    // prevHasSelectionRef in that case, so the transition is still detected
    // once business objects resolve, instead of being marked as "seen"
    // before we ever got to check the element type.
    if (businessObjects === undefined) {
      return;
    }

    const prevHasSelection = prevHasSelectionRef.current;
    prevHasSelectionRef.current = hasSelection;
    if (
      !hasSelection ||
      prevHasSelection ||
      modificationsStore.isModificationModeEnabled ||
      businessObjects[selectedElementId ?? '']?.$type !== 'bpmn:CallActivity'
    ) {
      return;
    }

    navigate(
      {
        pathname: Paths.processInstanceDetails({processInstanceId}),
        search: location.search,
      },
      {replace: true},
    );
  }, [
    hasSelection,
    selectedElementId,
    businessObjects,
    processInstanceId,
    location.search,
    navigate,
  ]);

  const incidentsCount = useSelectionAwareIncidentsCount(
    processInstanceId ?? '',
    hasIncident,
  );

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
      visible: hasSelection,
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
