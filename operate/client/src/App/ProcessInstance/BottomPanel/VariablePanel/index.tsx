/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';

import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {variablesStore} from 'modules/stores/variables';
import {TabView} from 'modules/components/TabView';
import {processInstanceListenersStore} from 'modules/stores/processInstanceListeners';
import {useProcessInstancePageParams} from '../../useProcessInstancePageParams';
import {InputOutputMappings} from './InputOutputMappings';
import {VariablesContent} from './VariablesContent';
import {Listeners} from './Listeners';
import {WarningFilled} from './styled';

const VariablePanel = observer(function VariablePanel() {
  const {processInstanceId = ''} = useProcessInstancePageParams();

  const flowNodeId = flowNodeSelectionStore.state.selection?.flowNodeId;
  const flowNodeInstanceId =
    flowNodeSelectionStore.state.selection?.flowNodeInstanceId;

  const {
    fetchListeners,
    state,
    listenersFailureCount,
    hasFlowNodeListeners,
    reset,
  } = processInstanceListenersStore;
  const {listeners, listenerTypeFilter} = state;

  const shouldUseFlowNodeId = !flowNodeInstanceId && flowNodeId;

  useEffect(() => {
    reset();
  }, [flowNodeId, reset]);

  useEffect(() => {
    variablesStore.init(processInstanceId);

    return () => {
      variablesStore.reset();
    };
  }, [processInstanceId]);

  useEffect(() => {
    if (shouldUseFlowNodeId) {
      fetchListeners({
        fetchType: 'initial',
        processInstanceId: processInstanceId,
        payload: {
          flowNodeId,
          ...(listenerTypeFilter && {listenerTypeFilter}),
        },
      });
    } else if (flowNodeInstanceId) {
      fetchListeners({
        fetchType: 'initial',
        processInstanceId: processInstanceId,
        payload: {
          flowNodeInstanceId,
          ...(listenerTypeFilter && {listenerTypeFilter}),
        },
      });
    }
  }, [
    fetchListeners,
    processInstanceId,
    flowNodeId,
    flowNodeInstanceId,
    shouldUseFlowNodeId,
    listenerTypeFilter,
  ]);

  return (
    <TabView
      tabs={[
        {
          id: 'variables',
          label: 'Variables',
          content: <VariablesContent />,
          removePadding: true,
          onClick: () => {
            variablesStore.startPolling(processInstanceId);
            variablesStore.refreshVariables(processInstanceId);
          },
        },
        ...(flowNodeSelectionStore.isRootNodeSelected
          ? []
          : [
              {
                id: 'input-mappings',
                label: 'Input Mappings',
                content: <InputOutputMappings type="Input" />,
                onClick: variablesStore.stopPolling,
              },
              {
                id: 'output-mappings',
                label: 'Output Mappings',
                content: <InputOutputMappings type="Output" />,
                onClick: variablesStore.stopPolling,
              },
            ]),
        ...(hasFlowNodeListeners
          ? [
              {
                id: 'listeners',
                testId: 'listeners-tab-button',
                ...(listenersFailureCount && {
                  labelIcon: <WarningFilled />,
                }),
                label: 'Listeners',
                content: <Listeners listeners={listeners} />,
                removePadding: true,
                onClick: variablesStore.stopPolling,
              },
            ]
          : []),
      ]}
      key={`tabview-has-listeners-${hasFlowNodeListeners}`}
    />
  );
});

export {VariablePanel};
