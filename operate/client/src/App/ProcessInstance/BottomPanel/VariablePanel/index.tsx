/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState} from 'react';
import {observer} from 'mobx-react';

import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {TabView} from 'modules/components/TabView';
import {useProcessInstancePageParams} from '../../useProcessInstancePageParams';
import {InputOutputMappings} from './InputOutputMappings';
import {VariablesContent} from './VariablesContent';
import {DetailsContent} from './DetailsContent';
import {IncidentsContent} from './IncidentsContent';
import {Listeners, type ListenerTypeFilter} from './Listeners';
import {OperationsLog} from './OperationsLog';
import {WarningFilled} from './styled';
import {useJobs} from 'modules/queries/jobs/useJobs';
import {useIsRootNodeSelected} from 'modules/hooks/flowNodeSelection';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useProcessInstanceIncidentsCount} from 'modules/queries/incidents/useProcessInstanceIncidentsCount';
import {useGetIncidentsByElementInstance} from 'modules/queries/incidents/useGetIncidentsByElementInstance';
import {useFlownodeInstancesStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics';

const tabIds = {
  incidents: 'incidents',
  details: 'details',
  variables: 'variables',
  inputMappings: 'input-mappings',
  outputMappings: 'output-mappings',
  listeners: 'listeners',
  operationsLog: 'operations-log',
} as const;

type TabId = (typeof tabIds)[keyof typeof tabIds];

type Props = {
  setListenerTabVisibility: React.Dispatch<React.SetStateAction<boolean>>;
};

const VariablePanel: React.FC<Props> = observer(function VariablePanel({
  setListenerTabVisibility,
}) {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const isRootNodeSelected = useIsRootNodeSelected();
  const {data: processInstance} = useProcessInstance();

  const flowNodeId = flowNodeSelectionStore.state.selection?.flowNodeId;
  const flowNodeInstanceId =
    flowNodeSelectionStore.state.selection?.flowNodeInstanceId;

  const [listenerTypeFilter, setListenerTypeFilter] =
    useState<ListenerTypeFilter>();
  const [selectedTab, setSelectedTab] = useState<TabId>('variables');

  const {data: statistics} = useFlownodeInstancesStatistics();

  // Get incident counts
  const processIncidentsCount = useProcessInstanceIncidentsCount(
    processInstanceId,
    {
      enabled: isRootNodeSelected && !!processInstance?.hasIncident,
    },
  );

  const {data: elementIncidentsData} = useGetIncidentsByElementInstance(
    flowNodeInstanceId ?? '',
    {
      enabled: !isRootNodeSelected && !!flowNodeInstanceId,
      select: (data) => data.page.totalItems,
    },
  );

  const elementIncidentsCount = elementIncidentsData ?? 0;

  // Get flow node incidents count from statistics
  const flowNodeIncidentsFromStats =
    !isRootNodeSelected && flowNodeId
      ? statistics?.items.find((item) => item.elementId === flowNodeId)?.incidents ?? 0
      : 0;

  // Use element incidents count if available, otherwise use statistics
  const elementIncidentsCountFinal =
    elementIncidentsCount > 0 ? elementIncidentsCount : flowNodeIncidentsFromStats;

  // Check if we should show incidents tab
  const shouldShowIncidentsTab =
    (isRootNodeSelected && processIncidentsCount > 0) ||
    (!isRootNodeSelected && elementIncidentsCountFinal > 0);

  const incidentsTabLabel = shouldShowIncidentsTab
    ? `Incidents (${isRootNodeSelected ? processIncidentsCount : elementIncidentsCountFinal})`
    : 'Incidents';

  const shouldFetchListeners = flowNodeInstanceId || flowNodeId;
  const {
    data: jobs,
    fetchNextPage,
    fetchPreviousPage,
    hasNextPage,
    hasPreviousPage,
  } = useJobs({
    payload: {
      filter: {
        processInstanceKey: processInstanceId,
        elementId: flowNodeId,
        elementInstanceKey: flowNodeInstanceId,
        kind: listenerTypeFilter,
      },
    },
    disabled: !shouldFetchListeners,
    select: (data) => data.pages?.flatMap((page) => page.items),
  });

  const hasFailedListeners = jobs?.some(({state}) => state === 'FAILED');

  return (
    <TabView
      onTabChange={(tabId) => setSelectedTab(tabId)}
      tabs={[
        ...(shouldShowIncidentsTab
          ? [
              {
                id: tabIds.incidents,
                label: incidentsTabLabel,
                content: <IncidentsContent />,
                removePadding: true,
                onClick: () => {
                  setListenerTabVisibility(false);
                },
              },
            ]
          : []),
        ...(isRootNodeSelected
          ? []
          : [
              {
                id: tabIds.details,
                label: 'Details',
                content: <DetailsContent />,
                removePadding: true,
                onClick: () => {
                  setListenerTabVisibility(false);
                },
              },
            ]),
        {
          id: tabIds.variables,
          label: 'Variables',
          content: <VariablesContent />,
          removePadding: true,
          onClick: () => {
            setListenerTabVisibility(false);
          },
        },
        ...(isRootNodeSelected
          ? []
          : [
              {
                id: tabIds.inputMappings,
                label: 'Input Mappings',
                content: <InputOutputMappings type="Input" />,
                onClick: () => {
                  setListenerTabVisibility(false);
                },
              },
              {
                id: tabIds.outputMappings,
                label: 'Output Mappings',
                content: <InputOutputMappings type="Output" />,
                onClick: () => {
                  setListenerTabVisibility(false);
                },
              },
            ]),
        {
          id: tabIds.listeners,
          testId: 'listeners-tab-button',
          ...(hasFailedListeners && {
            labelIcon: <WarningFilled />,
          }),
          label: 'Listeners',
          content: (
            <Listeners
              jobs={jobs}
              setListenerTypeFilter={setListenerTypeFilter}
              fetchNextPage={fetchNextPage}
              fetchPreviousPage={fetchPreviousPage}
              hasNextPage={hasNextPage}
              hasPreviousPage={hasPreviousPage}
            />
          ),
          removePadding: true,
          onClick: () => {
            setListenerTabVisibility(true);
          },
        },
        {
          id: tabIds.operationsLog,
          label: 'Operations log',
          content: (
            <OperationsLog
              isRootNodeSelected={isRootNodeSelected}
              flowNodeInstanceId={flowNodeInstanceId}
              isVisible={selectedTab === tabIds.operationsLog}
            />
          ),
          removePadding: true,
          onClick: () => {
            setListenerTabVisibility(true);
          },
        },
      ]}
      key={`tabview-${flowNodeId}-${flowNodeInstanceId}`}
    />
  );
});

export {VariablePanel};
