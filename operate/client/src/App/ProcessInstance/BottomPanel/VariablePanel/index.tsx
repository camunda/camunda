/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {observer} from 'mobx-react';

import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {variablesStore} from 'modules/stores/variables';
import {TabView} from 'modules/components/TabView';
import {IS_LISTENERS_TAB_SUPPORTED} from 'modules/feature-flags';
import {processInstanceListenersStore} from 'modules/stores/processInstanceListeners';
import {useProcessInstancePageParams} from '../../useProcessInstancePageParams';
import {InputOutputMappings} from './InputOutputMappings';
import {VariablesContent} from './VariablesContent';
import {Listeners} from './Listeners';

const VariablePanel = observer(function VariablePanel() {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const [flowNodeHasListeners, setFlowNodeHasListeners] = useState(false);

  const flowNodeId = flowNodeSelectionStore.state.selection?.flowNodeId;

  const {fetchListeners, state} = processInstanceListenersStore;
  const {listeners} = state;

  useEffect(() => {
    variablesStore.init(processInstanceId);

    return () => {
      variablesStore.reset();
    };
  }, [processInstanceId]);

  useEffect(() => {
    if (flowNodeId) {
      fetchListeners('initial', processInstanceId, {flowNodeId});
    }
  }, [fetchListeners, processInstanceId, flowNodeId]);

  useEffect(() => {
    listeners.length > 0
      ? setFlowNodeHasListeners(true)
      : setFlowNodeHasListeners(false);
  }, [listeners]);

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
              ...(IS_LISTENERS_TAB_SUPPORTED && flowNodeHasListeners
                ? [
                    {
                      id: 'listeners',
                      label: 'Listeners',
                      content: <Listeners listeners={listeners} />,
                      onClick: () => {},
                    },
                  ]
                : []),
            ]),
      ]}
    />
  );
});

export {VariablePanel};
