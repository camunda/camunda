/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useEffect} from 'react';
import {observer} from 'mobx-react';

import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {variablesStore} from 'modules/stores/variables';
import {TabView} from 'modules/components/TabView';
import {processInstanceListenersStore} from 'modules/stores/processInstanceListeners';
import {useProcessInstancePageParams} from '../../../useProcessInstancePageParams';
import {InputOutputMappings} from '../InputOutputMappings';
import {VariablesContent as VariablesContentV2} from './VariablesContent';
import {Listeners} from './Listeners';
import {WarningFilled} from '../styled';
import {init, startPolling} from 'modules/utils/variables';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useJobs} from 'modules/queries/jobs/useJobs';
import {useIsRootNodeSelected} from 'modules/hooks/flowNodeSelection';

type Props = {
  setListenerTabVisibility: React.Dispatch<React.SetStateAction<boolean>>;
};

const VariablePanel: React.FC<Props> = observer(function VariablePanel({
  setListenerTabVisibility,
}) {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const {data: processInstance} = useProcessInstance();
  const isRootNodeSelected = useIsRootNodeSelected();

  const {data: jobs} = useJobs({
    filter: {processInstanceKey: {$eq: processInstanceId}},
  });
  // if (jobs) {
  //   processInstanceListenersStore.setListeners({
  //     listenersCount: jobs.page.totalItems,
  //     listeners: jobs.items.map((job) => ({
  //       listenerKey: job.jobKey,
  //       jobType: job.type,
  //       state: job.state,
  //       listenerType: job.kind,
  //       event: job.listenerEventType,
  //       time: job.endTime,
  //       sortValues: [new Date(job.endTime).valueOf().toString(), job.jobKey],
  //     })),
  //   });
  // }

  // console.log({processInstanceId, jobs});

  const flowNodeId = flowNodeSelectionStore.state.selection?.flowNodeId;
  const flowNodeInstanceId =
    flowNodeSelectionStore.state.selection?.flowNodeInstanceId;

  const {listenersFailureCount, state, fetchListeners, reset} =
    processInstanceListenersStore;
  const {listenerTypeFilter} = state;

  const shouldUseFlowNodeId = !flowNodeInstanceId && flowNodeId;

  useEffect(() => {
    reset();
  }, [flowNodeId, flowNodeInstanceId, reset]);

  useEffect(() => {
    init(processInstance);

    return () => {
      variablesStore.reset();
    };
  }, [processInstance]);

  // useEffect(() => {
  //   if (shouldUseFlowNodeId) {
  //     fetchListeners({
  //       fetchType: 'initial',
  //       processInstanceId: processInstanceId,
  //       payload: {
  //         flowNodeId,
  //         ...(listenerTypeFilter && {listenerTypeFilter}),
  //       },
  //     });
  //   } else if (flowNodeInstanceId) {
  //     fetchListeners({
  //       fetchType: 'initial',
  //       processInstanceId: processInstanceId,
  //       payload: {
  //         flowNodeInstanceId,
  //         ...(listenerTypeFilter && {listenerTypeFilter}),
  //       },
  //     });
  //   }
  // }, [
  //   fetchListeners,
  //   processInstanceId,
  //   flowNodeId,
  //   flowNodeInstanceId,
  //   shouldUseFlowNodeId,
  //   listenerTypeFilter,
  // ]);

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
            startPolling(processInstance);
            variablesStore.refreshVariables(processInstanceId);
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
                  return variablesStore.stopPolling;
                },
              },
              {
                id: 'output-mappings',
                label: 'Output Mappings',
                content: <InputOutputMappings type="Output" />,
                onClick: () => {
                  setListenerTabVisibility(false);
                  return variablesStore.stopPolling;
                },
              },
            ]),
        {
          id: 'listeners',
          testId: 'listeners-tab-button',
          ...(listenersFailureCount && {
            labelIcon: <WarningFilled />,
          }),
          label: 'Listeners',
          content: <Listeners jobs={jobs} />,
          removePadding: true,
          onClick: () => {
            setListenerTabVisibility(true);
            return variablesStore.stopPolling;
          },
        },
      ]}
      key={`tabview-${flowNodeId}-${flowNodeInstanceId}`}
    />
  );
});

export {VariablePanel};
