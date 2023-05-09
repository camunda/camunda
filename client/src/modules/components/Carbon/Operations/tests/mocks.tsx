/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {createInstance, createOperation} from 'modules/testUtils';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstancesStore} from 'modules/stores/processInstances';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {LocationLog} from 'modules/utils/LocationLog';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return () => {
      modificationsStore.reset();
      processInstancesStore.reset();
    };
  }, []);

  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/carbon/processes']}>
        <Routes>
          <Route
            path="/carbon/processes/:processInstanceId"
            element={children}
          />
          <Route path="/carbon/processes" element={children} />
        </Routes>
        <LocationLog />
      </MemoryRouter>
    </ThemeProvider>
  );
};

const INSTANCE = createInstance({
  id: 'instance_1',
  operations: [createOperation({state: 'FAILED'})],
  hasActiveOperation: false,
});
const ACTIVE_INSTANCE = createInstance({
  id: 'instance_1',
  operations: [createOperation({state: 'SENT'})],
  hasActiveOperation: true,
});

export {Wrapper, INSTANCE, ACTIVE_INSTANCE};
