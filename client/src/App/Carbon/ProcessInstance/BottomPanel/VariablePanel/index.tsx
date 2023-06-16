/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {when} from 'mobx';

import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {useEffect} from 'react';
import {variablesStore} from 'modules/stores/variables';
import {TabView} from 'modules/components/Carbon/TabView';
import {InputOutputMappings} from './InputOutputMappings';
import {VariablesContent} from './VariablesContent';

const VariablePanel = observer(function VariablePanel() {
  useEffect(() => {
    const variablesLoadedCheckDisposer = when(
      () => ['fetched', 'error'].includes(variablesStore.state.status),
      () => {
        variablesStore.setAreVariablesLoadedOnce(true);
      }
    );

    return () => {
      variablesStore.setAreVariablesLoadedOnce(false);
      variablesLoadedCheckDisposer();
    };
  }, []);

  return (
    <TabView
      tabs={[
        {
          id: 'variables',
          label: 'Variables',
          content: <VariablesContent />,
          removePadding: true,
        },
        ...(flowNodeSelectionStore.isRootNodeSelected
          ? []
          : [
              {
                id: 'input-mappings',
                label: 'Input Mappings',
                content: <InputOutputMappings type="Input" />,
              },
              {
                id: 'output-mappings',
                label: 'Output Mappings',
                content: <InputOutputMappings type="Output" />,
              },
            ]),
      ]}
    />
  );
});

export {VariablePanel};
