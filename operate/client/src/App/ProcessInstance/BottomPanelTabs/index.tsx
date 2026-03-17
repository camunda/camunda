/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Outlet} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {Container, TabContent} from './styled';
import {TabListNav} from './TabListNav';
import {useProcessInstancePageParams} from '../useProcessInstancePageParams';
import {useCurrentPage} from 'modules/hooks/useCurrentPage';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useProcessInstanceIncidentsCount} from 'modules/queries/incidents/useProcessInstanceIncidentsCount';

const BottomPanelTabs: React.FC = () => {
  const {hasSelection} = useProcessInstanceElementSelection();
  const {data: processInstance} = useProcessInstance();
  const {processInstanceId} = useProcessInstancePageParams();
  const {currentPage} = useCurrentPage();
  const hasIncident = processInstance?.hasIncident === true;
  const incidentsCount = useProcessInstanceIncidentsCount(
    processInstanceId ?? '',
    {enabled: hasIncident},
  );
  const tabItems = [
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
      label: 'Incidents',
      to: {pathname: Paths.processInstanceIncidents({processInstanceId})},
      key: 'incidents',
      selected: currentPage === 'process-details-incidents',
      title: 'Incidents',
      visible: hasIncident,
      count: incidentsCount,
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

  return (
    <Container>
      <TabListNav label="Process Instance Bottom Panel Tabs" items={tabItems} />
      <TabContent>
        <Outlet />
      </TabContent>
    </Container>
  );
};

export {BottomPanelTabs};
