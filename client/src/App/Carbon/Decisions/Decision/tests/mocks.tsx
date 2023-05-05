/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {MemoryRouter} from 'react-router-dom';
import {useEffect} from 'react';
import {authenticationStore} from 'modules/stores/authentication';

function createWrapper(initialPath: string = '/') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      decisionXmlStore.init();
      groupedDecisionsStore.fetchDecisions();

      return () => {
        decisionXmlStore.reset();
        groupedDecisionsStore.reset();
        authenticationStore.reset();
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
