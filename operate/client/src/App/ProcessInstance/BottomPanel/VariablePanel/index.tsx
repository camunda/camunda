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
import {Listeners, type ListenerTypeFilter} from './Listeners';
import {OperationsLog} from './OperationsLog';
import {WarningFilled} from './styled';
import {useJobs} from 'modules/queries/jobs/useJobs';
import {useIsRootNodeSelected} from 'modules/hooks/flowNodeSelection';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {IS_ELEMENT_SELECTION_V2} from 'modules/feature-flags';

const tabIds = {
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
  let {hasSelection, selectedElementId, selectedElementInstanceKey} =
    useProcessInstanceElementSelection();

  // TODO: Remove these assignments to remove the feature flag from the component
  hasSelection = IS_ELEMENT_SELECTION_V2
    ? hasSelection
    : // eslint-disable-next-line react-hooks/rules-of-hooks
      !useIsRootNodeSelected();
  selectedElementId = IS_ELEMENT_SELECTION_V2
    ? selectedElementId
    : (flowNodeSelectionStore.state.selection?.flowNodeId ?? null);
  selectedElementInstanceKey = IS_ELEMENT_SELECTION_V2
    ? selectedElementInstanceKey
    : (flowNodeSelectionStore.state.selection?.flowNodeInstanceId ?? null);

  const [listenerTypeFilter, setListenerTypeFilter] =
    useState<ListenerTypeFilter>();
  const [selectedTab, setSelectedTab] = useState<TabId>('variables');

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
        elementId: selectedElementId ?? undefined,
        elementInstanceKey: selectedElementInstanceKey ?? undefined,
        kind: listenerTypeFilter,
      },
    },
    disabled: !hasSelection,
    select: (data) => data.pages?.flatMap((page) => page.items),
  });

  const hasFailedListeners = jobs?.some(({state}) => state === 'FAILED');

  return (
    <TabView
      key={`tabview-${selectedElementId}-${selectedElementInstanceKey}`}
      onTabChange={(tabId) => setSelectedTab(tabId)}
      tabs={[
        {
          id: tabIds.variables,
          label: 'Variables',
          content: <VariablesContent />,
          removePadding: true,
          onClick: () => {
            setListenerTabVisibility(false);
          },
        },
        ...(!hasSelection || !selectedElementId
          ? []
          : [
              {
                id: tabIds.inputMappings,
                label: 'Input Mappings',
                content: (
                  <InputOutputMappings
                    type="Input"
                    elementId={selectedElementId}
                  />
                ),
                onClick: () => {
                  setListenerTabVisibility(false);
                },
              },
              {
                id: tabIds.outputMappings,
                label: 'Output Mappings',
                content: (
                  <InputOutputMappings
                    type="Output"
                    elementId={selectedElementId}
                  />
                ),
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
          label: 'Operations Log',
          content: (
            <OperationsLog isVisible={selectedTab === tabIds.operationsLog} />
          ),
          removePadding: true,
          onClick: () => {
            setListenerTabVisibility(false);
          },
        },
      ]}
    />
  );
});

export {VariablePanel};
