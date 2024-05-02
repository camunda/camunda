/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';

import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {useEffect} from 'react';
import {variablesStore} from 'modules/stores/variables';
import {TabView} from 'modules/components/TabView';
import {InputOutputMappings} from './InputOutputMappings';
import {VariablesContent} from './VariablesContent';
import {useProcessInstancePageParams} from '../../useProcessInstancePageParams';

const VariablePanel = observer(function VariablePanel() {
  const {processInstanceId = ''} = useProcessInstancePageParams();

  useEffect(() => {
    variablesStore.init(processInstanceId);

    return () => {
      variablesStore.reset();
    };
  }, [processInstanceId]);

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
      ]}
    />
  );
});

export {VariablePanel};
