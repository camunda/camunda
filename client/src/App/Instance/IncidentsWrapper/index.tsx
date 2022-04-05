/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect} from 'react';

import IncidentsOverlay from './IncidentsOverlay';
import {IncidentsTable} from './IncidentsTable';
import {IncidentsFilter} from './IncidentsFilter';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';

import * as Styled from './styled';

type Props = {
  setIsInTransition: (isTransitionActive: boolean) => void;
};

const IncidentsWrapper: React.FC<Props> = observer(({setIsInTransition}) => {
  useEffect(() => {
    incidentsStore.init();

    return () => {
      incidentsStore.reset();
    };
  }, []);

  if (incidentsStore.incidentsCount === 0) {
    return null;
  }

  return (
    <>
      <Styled.Transition
        in={incidentsStore.state.isIncidentBarOpen}
        onEnter={() => setIsInTransition(true)}
        onEntered={() => setIsInTransition(false)}
        onExit={() => setIsInTransition(true)}
        onExited={() => setIsInTransition(false)}
        mountOnEnter
        unmountOnExit
        timeout={400}
      >
        <IncidentsOverlay>
          <IncidentsFilter />
          <IncidentsTable />
        </IncidentsOverlay>
      </Styled.Transition>
    </>
  );
});

export {IncidentsWrapper};
