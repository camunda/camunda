/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {Outlet} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {Container} from './styled';
import {TabListNav} from './TabListNav';
import {useProcessInstancePageParams} from '../useProcessInstancePageParams';
import {useCurrentPage} from 'modules/hooks/useCurrentPage';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';

const BottomPanelTabs: React.FC = () => {
  const {hasSelection} = useProcessInstanceElementSelection();
  const {data: processInstance} = useProcessInstance();
  const {processInstanceId} = useProcessInstancePageParams();
  const {currentPage} = useCurrentPage();
  const tabItems = useMemo(() => {
    return [
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
        to: {pathname: Paths.processInstance(processInstanceId)},
        key: 'variables',
        selected: currentPage === 'process-details',
        title: 'Variables',
        visible: true,
      },
      {
        label: 'Incidents',
        to: {pathname: Paths.processInstanceIncidents({processInstanceId})},
        key: 'incidents',
        selected: currentPage === 'process-details-incidents',
        title: 'Incidents',
        visible: processInstance?.hasIncident === true,
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
  }, [
    processInstanceId,
    currentPage,
    hasSelection,
    processInstance?.hasIncident,
  ]);

  return (
    <Container>
      <TabListNav label="Process Instance Bottom Panel Tabs" items={tabItems} />
      <Outlet />
    </Container>
  );
};

export {BottomPanelTabs};
