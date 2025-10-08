/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IncidentsOverlay} from './IncidentsOverlay';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';
import {Transition} from './styled';
import {IncidentsFilter} from './IncidentsFilter';
import {IncidentsTable} from './IncidentsTable';
import {IncidentsTable as IncidentsTableV2} from './IncidentsTable/v2';
import {PanelHeader} from 'modules/components/PanelHeader';
import {
  getFilteredIncidents,
  getFilteredIncidentsV2,
  init,
} from 'modules/utils/incidents';
import {useIncidents, useIncidentsV2} from 'modules/hooks/incidents';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {useEffect} from 'react';
import {IS_INCIDENTS_PANEL_V2} from 'modules/feature-flags';

type Props = {
  processInstance: ProcessInstance;
  setIsInTransition: (isTransitionActive: boolean) => void;
};

const IncidentsWrapper: React.FC<Props> = observer(
  ({setIsInTransition, processInstance}) => {
    if (IS_INCIDENTS_PANEL_V2) {
      // Having a condition before hooks is usually not allowed but works this time,
      // because the condition is static during runtime.
      return (
        <IncidentsWrapperV2
          setIsInTransition={setIsInTransition}
          processInstance={processInstance}
        />
      );
    }

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

const IncidentsWrapperV2: React.FC<Props> = observer(
  ({setIsInTransition, processInstance}) => {
    const incidents = useIncidentsV2(processInstance.processInstanceKey);
    const filteredIncidents = getFilteredIncidentsV2(incidents);

    if (incidents.length === 0) {
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
            <IncidentsTableV2
              processInstanceKey={processInstance.processInstanceKey}
              incidents={filteredIncidents}
            />
          </IncidentsOverlay>
        </Transition>
      </>
    );
  },
);

export {IncidentsWrapper, IncidentsWrapperV2};
