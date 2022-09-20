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

const GROUPED_PROCESSES = [
  GROUPED_BIG_VARIABLE_PROCESS,
  {
    bpmnProcessId: 'complexProcess',
    name: null,
    processes: [
      {
        id: '2251799813687825',
        name: null,
        version: 3,
        bpmnProcessId: 'complexProcess',
      },
      {
        id: '2251799813686926',
        name: null,
        version: 2,
        bpmnProcessId: 'complexProcess',
      },
      {
        id: '2251799813685540',
        name: null,
        version: 1,
        bpmnProcessId: 'complexProcess',
      },
    ],
  },
] as const;

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

export {GROUPED_BIG_VARIABLE_PROCESS, GROUPED_PROCESSES, getWrapper};
