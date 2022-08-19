/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {VariablesContent} from './VariablesContent';
import {observer} from 'mobx-react';
import {when} from 'mobx';

import {VariablesPanel} from './styled';
import {TabView} from 'modules/components/TabView';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {useEffect} from 'react';
import {variablesStore} from 'modules/stores/variables';
import {InputOutputMappings} from './InputOutputMappings';

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
    <VariablesPanel>
      <TabView
        tabs={[
          {
            id: 'variables',
            label: 'Variables',
            content: <VariablesContent />,
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
    </VariablesPanel>
  );
});

export {VariablePanel};
