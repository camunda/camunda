/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {LocationLog} from 'modules/utils/LocationLog';
import {useEffect} from 'react';
import {processesStore} from 'modules/stores/processes/processes.list';
import {processXmlStore} from 'modules/stores/processXml';
import {Paths} from 'modules/Routes';

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

function getWrapper(initialPath: string = Paths.dashboard()) {
  const MockApp: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        processesStore.reset();
        processXmlStore.reset();
      };
    }, []);

    return (
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path={Paths.dashboard()} element={children} />
        </Routes>
        <LocationLog />
      </MemoryRouter>
    );
  };

  return MockApp;
}

export {GROUPED_BIG_VARIABLE_PROCESS, getWrapper};
