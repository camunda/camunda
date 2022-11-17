/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Link, MemoryRouter} from 'react-router-dom';
import {NotificationProvider} from 'modules/notifications';
import {ListFooter} from '../ListFooter';
import {createInstance, createOperation} from 'modules/testUtils';

function createWrapper(initialPath: string = '/') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <ThemeProvider>
        <NotificationProvider>
          <MemoryRouter initialEntries={[initialPath]}>
            {children}
            <ListFooter />
            <Link to="/processes?incidents=true&active=true&process=bigVarProcess">
              go to big var
            </Link>
          </MemoryRouter>
        </NotificationProvider>
      </ThemeProvider>
    );
  };
  return Wrapper;
}

const INSTANCE = createInstance({
  id: '1',
  operations: [createOperation({state: 'FAILED'})],
  hasActiveOperation: false,
});

const ACTIVE_INSTANCE = createInstance({
  id: '2',
  operations: [createOperation({state: 'SENT'})],
  hasActiveOperation: true,
});

export {createWrapper, INSTANCE, ACTIVE_INSTANCE};
