/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {createIncident} from 'modules/testUtils';
import {useEffect} from 'react';
import {authenticationStore} from 'modules/stores/authentication';
import {incidentsStore} from 'modules/stores/incidents';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return () => {
      incidentsStore.reset();
      authenticationStore.reset();
      flowNodeSelectionStore.reset();
      processInstanceDetailsDiagramStore.reset();
    };
  });
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/processes/1']}>
        <Routes>
          <Route path="/processes/:processInstanceId" element={children} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>
  );
};

const id = 'flowNodeInstanceIdB';
const shortError = 'No data found for query $.orderId.';
const longError =
  'Cannot compare values of different types: INTEGER and BOOLEAN';

const firstIncident = createIncident({
  errorType: {name: 'Error A', id: 'ERROR_A'},
  errorMessage: shortError,
  flowNodeId: 'StartEvent_1',
  flowNodeInstanceId: '18239123812938',
  rootCauseInstance: {
    instanceId: '111111111111111111',
    processDefinitionId: 'calledInstance',
    processDefinitionName: 'Called Instance',
  },
});

const secondIncident = createIncident({
  errorType: {name: 'Error B', id: 'ERROR_A'},
  errorMessage: longError,
  flowNodeId: 'Event_1db567d',
  flowNodeInstanceId: id,
});

const incidentsMock = {
  incidents: [firstIncident, secondIncident],
  count: 2,
  errorTypes: [],
  flowNodes: [],
};

export {Wrapper, incidentsMock, firstIncident, secondIncident, shortError};
