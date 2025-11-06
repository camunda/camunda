/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Loading} from '@carbon/react';
import {incidentsStore} from 'modules/stores/incidents';
import {useGetIncidentsByElementInstance} from 'modules/queries/incidents/useGetIncidentsByElementInstance';
import {MultiIncidents} from './multiIncidents';
import {SingleIncident} from './singleIncident';
import {IS_INCIDENTS_PANEL_V2} from 'modules/feature-flags';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';
import {Divider} from '../styled';

type Props = {
  elementInstanceKey: string;
  elementName: string;
  elementId: string;
};

const Incidents: React.FC<Props> = ({
  elementInstanceKey,
  elementName,
  elementId,
}) => {
  const {data, isLoading: isSearchingIncidents} =
    useGetIncidentsByElementInstance(elementInstanceKey);

  const singleIncident = data?.page.totalItems === 1 ? data?.items[0] : null;
  const multiIncidents = data && data?.page.totalItems > 1 ? data.items : null;

  return (
    <>
      {isSearchingIncidents ? (
        <Loading small withOverlay={false} data-testid="incidents-loading" />
      ) : singleIncident ? (
        <>
          <Divider />
          <SingleIncident
            incident={singleIncident}
            onButtonClick={() => {
              if (IS_INCIDENTS_PANEL_V2) {
                return incidentsPanelStore.showIncidentsForElementInstance(
                  elementInstanceKey,
                  elementName,
                );
              }
              incidentsStore.clearSelection();
              incidentsStore.toggleFlowNodeSelection(elementId);
              incidentsStore.toggleErrorTypeSelection(singleIncident.errorType);
              incidentsStore.setIncidentBarOpen(true);
            }}
          />
        </>
      ) : data && multiIncidents ? (
        <>
          <Divider />
          <MultiIncidents
            count={data.page.totalItems}
            onButtonClick={() => {
              if (IS_INCIDENTS_PANEL_V2) {
                return incidentsPanelStore.showIncidentsForElementInstance(
                  elementInstanceKey,
                  elementName,
                );
              }
              incidentsStore.clearSelection();
              incidentsStore.toggleFlowNodeSelection(elementId);
              incidentsStore.setIncidentBarOpen(true);
            }}
          />
        </>
      ) : null}
    </>
  );
};

export {Incidents};
