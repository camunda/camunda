/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useEffect} from 'react';

import {IncidentsOverlay} from './IncidentsOverlay';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';
import {Transition} from './styled';
import {IncidentsFilter} from './IncidentsFilter';
import {IncidentsTable} from './IncidentsTable';
import {PanelHeader} from 'modules/components/PanelHeader';
import {getFilteredIncidents, init} from 'modules/utils/incidents';
import {useIncidents} from 'modules/hooks/incidents';
import {type ProcessInstance} from '@vzeta/camunda-api-zod-schemas/8.8';

type Props = {
  processInstance: ProcessInstance;
  setIsInTransition: (isTransitionActive: boolean) => void;
};

const IncidentsWrapper: React.FC<Props> = observer(
  ({setIsInTransition, processInstance}) => {
    const incidents = useIncidents();
    const filteredIncidents = getFilteredIncidents(incidents);

    useEffect(() => {
      init(processInstance);

      return () => {
        incidentsStore.reset();
      };
    }, [processInstance]);

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
              count={filteredIncidents.length}
              size="sm"
            >
              <IncidentsFilter />
            </PanelHeader>
            <IncidentsTable />
          </IncidentsOverlay>
        </Transition>
      </>
    );
  },
);

export {IncidentsWrapper};
