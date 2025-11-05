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
import {useIncidents, useEnhancedIncidents} from 'modules/hooks/incidents';
import {
  type Incident,
  type ProcessInstance,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {useEffect} from 'react';
import {IS_INCIDENTS_PANEL_V2} from 'modules/feature-flags';
import {isInstanceRunning} from 'modules/utils/instance';
import {modificationsStore} from 'modules/stores/modifications';
import {useGetIncidentsByProcessInstance} from 'modules/queries/incidents/useGetIncidentsByProcessInstance';
import {useGetIncidentsByElementInstance} from 'modules/queries/incidents/useGetIncidentsByElementInstance';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';

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
              <IncidentsFilter
                processInstanceKey={processInstance.processInstanceKey}
              />
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
    const enablePeriodicRefetch =
      isInstanceRunning(processInstance) &&
      !modificationsStore.isModificationModeEnabled;
    const selectedElementInstance =
      incidentsPanelStore.state.selectedElementInstance;

    if (selectedElementInstance !== null) {
      return (
        <IncidentsByElementInstance
          elementInstanceKey={selectedElementInstance.elementInstanceKey}
          enablePeriodicRefetch={enablePeriodicRefetch}
        >
          {(incidents) => (
            <IncidentsWrapperContent
              incidents={incidents}
              processInstanceKey={processInstance.processInstanceKey}
              setIsInTransition={setIsInTransition}
            />
          )}
        </IncidentsByElementInstance>
      );
    }

    return (
      <IncidentsByProcessInstance
        processInstanceKey={processInstance.processInstanceKey}
        enablePeriodicRefetch={enablePeriodicRefetch}
      >
        {(incidents) => (
          <IncidentsWrapperContent
            incidents={incidents}
            processInstanceKey={processInstance.processInstanceKey}
            setIsInTransition={setIsInTransition}
          />
        )}
      </IncidentsByProcessInstance>
    );
  },
);

type IncidentsWrapperContentProps = {
  incidents: Incident[];
  processInstanceKey: string;
  setIsInTransition: Props['setIsInTransition'];
};

const IncidentsWrapperContent: React.FC<IncidentsWrapperContentProps> =
  observer((props) => {
    const enhancedIncidents = useEnhancedIncidents(props.incidents ?? []);
    const filteredIncidents = getFilteredIncidentsV2(enhancedIncidents);
    const selectedElementInstance =
      incidentsPanelStore.state.selectedElementInstance;

    const headerTitle =
      selectedElementInstance !== null
        ? `Incidents - Filtered by "${selectedElementInstance.elementName}"`
        : 'Incidents';

    return (
      <Transition
        in={incidentsPanelStore.state.isPanelVisible}
        onEnter={() => props.setIsInTransition(true)}
        onEntered={() => props.setIsInTransition(false)}
        onExit={() => props.setIsInTransition(true)}
        onExited={() => props.setIsInTransition(false)}
        mountOnEnter
        unmountOnExit
        timeout={400}
      >
        <IncidentsOverlay>
          <PanelHeader
            title={headerTitle}
            count={filteredIncidents.length}
            size="sm"
          >
            <IncidentsFilter processInstanceKey={props.processInstanceKey} />
          </PanelHeader>
          <IncidentsTableV2
            processInstanceKey={props.processInstanceKey}
            incidents={filteredIncidents}
          />
        </IncidentsOverlay>
      </Transition>
    );
  });

type IncidentsSourceProps<Source> = Source & {
  enablePeriodicRefetch: boolean;
  children: (incidents: Incident[]) => React.ReactNode;
};

const IncidentsByProcessInstance: React.FC<
  IncidentsSourceProps<{processInstanceKey: string}>
> = (props) => {
  const {data: incidents} = useGetIncidentsByProcessInstance(
    props.processInstanceKey,
    {
      enablePeriodicRefetch: props.enablePeriodicRefetch,
      select: (res) => res.items,
    },
  );

  if (!incidents || incidents.length === 0) {
    return null;
  }

  return props.children(incidents);
};

const IncidentsByElementInstance: React.FC<
  IncidentsSourceProps<{elementInstanceKey: string}>
> = (props) => {
  const {data: incidents} = useGetIncidentsByElementInstance(
    props.elementInstanceKey,
    {
      enablePeriodicRefetch: props.enablePeriodicRefetch,
      select: (res) => res.items,
    },
  );

  if (!incidents || incidents.length === 0) {
    return null;
  }

  return props.children(incidents);
};

export {IncidentsWrapper, IncidentsWrapperV2};
