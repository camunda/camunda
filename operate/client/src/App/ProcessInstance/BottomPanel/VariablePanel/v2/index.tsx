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
import {useProcessInstancePageParams} from '../../../useProcessInstancePageParams';
import {InputOutputMappings} from '../InputOutputMappings';
import {VariablesContent as VariablesContentV2} from './VariablesContent';
import {Listeners} from './Listeners';
import {WarningFilled} from '../styled';
import {useJobs} from 'modules/queries/jobs/useJobs';
import {useIsRootNodeSelected} from 'modules/hooks/flowNodeSelection';

type Props = {
  setListenerTabVisibility: React.Dispatch<React.SetStateAction<boolean>>;
};

const VariablePanel: React.FC<Props> = observer(function VariablePanel({
  setListenerTabVisibility,
}) {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const isRootNodeSelected = useIsRootNodeSelected();

  const flowNodeId = flowNodeSelectionStore.state.selection?.flowNodeId;
  const flowNodeInstanceId =
    flowNodeSelectionStore.state.selection?.flowNodeInstanceId;

  const [listenerTypeFilter, setListenerTypeFilter] =
    useState<ListenerEntity['listenerType']>();

  const shouldUseFlowNodeId = !flowNodeInstanceId && flowNodeId;

  let jobsFilter = {};
  if (shouldUseFlowNodeId) {
    jobsFilter = {
      processInstanceKey: {$eq: processInstanceId},
      elementId: {$eq: flowNodeId},
      ...(listenerTypeFilter && {kind: {$eq: listenerTypeFilter}}),
    };
  } else if (flowNodeInstanceId) {
    jobsFilter = {
      processInstanceKey: {$eq: processInstanceId},
      elementId: {$eq: flowNodeInstanceId},
      ...(listenerTypeFilter && {kind: {$eq: listenerTypeFilter}}),
    };
  }
  const {
    data: jobs,
    fetchNextPage,
    fetchPreviousPage,
    hasNextPage,
    hasPreviousPage,
  } = useJobs({
    payload: {filter: jobsFilter},
    disabled: !shouldUseFlowNodeId && !flowNodeInstanceId,
    select: (data) => data.pages?.flatMap((page) => page.items),
  });

  const hasFailedListeners = jobs?.some(({state}) => state === 'FAILED');

  return (
    <TabView
      tabs={[
        {
          id: 'variables',
          label: 'Variables',
          content: <VariablesContentV2 />,
          removePadding: true,
          onClick: () => {
            setListenerTabVisibility(false);
          },
        },
        ...(isRootNodeSelected
          ? []
          : [
              {
                id: 'input-mappings',
                label: 'Input Mappings',
                content: <InputOutputMappings type="Input" />,
                onClick: () => {
                  setListenerTabVisibility(false);
                },
              },
              {
                id: 'output-mappings',
                label: 'Output Mappings',
                content: <InputOutputMappings type="Output" />,
                onClick: () => {
                  setListenerTabVisibility(false);
                },
              },
            ]),
        {
          id: 'listeners',
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
      ]}
      key={`tabview-${flowNodeId}-${flowNodeInstanceId}`}
    />
  );
});

export {VariablePanel};
