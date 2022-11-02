/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {LocationLog} from 'modules/utils/LocationLog';

const GROUPED_BIG_VARIABLE_PROCESS = {
  bpmnProcessId: 'bigVarProcess',
  name: 'Big variable process',
  processes: [
    {
      id: '2251799813685530',
      name: 'Big variable process',
      version: 1,
      bpmnProcessId: 'bigVarProcess',
    },
  ],
};

function getWrapper(initialPath: string = '/') {
  const MockApp: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path="/" element={children} />
          </Routes>
          <LocationLog />
        </MemoryRouter>
      </ThemeProvider>
    );
  };

  return MockApp;
}

export {GROUPED_BIG_VARIABLE_PROCESS, getWrapper};
