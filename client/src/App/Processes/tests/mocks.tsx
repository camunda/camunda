/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {MemoryRouter} from 'react-router-dom';
import {useEffect} from 'react';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {processInstancesStore} from 'modules/stores/processInstances';
import {processDiagramStore} from 'modules/stores/processDiagram';
import {operationsStore} from 'modules/stores/operations';
import {processesStore} from 'modules/stores/processes';

function createWrapper(initialPath: string = '/') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        processInstancesSelectionStore.reset();
        processInstancesStore.reset();
        processDiagramStore.reset();
        operationsStore.reset();
        processesStore.reset();
      };
    }, []);

    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

export {createWrapper};
