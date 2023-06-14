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
          content: <div>variables</div>,
        },
        ...(flowNodeSelectionStore.isRootNodeSelected
          ? []
          : [
              {
                id: 'input-mappings',
                label: 'Input Mappings',
                content: <div>input mappings</div>,
              },
              {
                id: 'output-mappings',
                label: 'Output Mappings',
                content: <div>output mappings</div>,
              },
            ]),
      ]}
    />
  );
});

export {VariablePanel};
