/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect} from 'react';

import {IncidentsOverlay} from './IncidentsOverlay';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';
import {Transition} from './styled';
import {IncidentsFilter} from './IncidentsFilter';
import {IncidentsTable} from './IncidentsTable';
import {PanelHeader} from 'modules/components/Carbon/PanelHeader';

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
      <Transition
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
          <PanelHeader
            title="Incidents View"
            count={incidentsStore.filteredIncidents.length}
            size="sm"
          >
            <IncidentsFilter />
          </PanelHeader>
          <IncidentsTable />
        </IncidentsOverlay>
      </Transition>
    </>
  );
});

export {IncidentsWrapper};
