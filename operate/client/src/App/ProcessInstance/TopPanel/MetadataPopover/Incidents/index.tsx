/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Loading} from '@carbon/react';
import {useGetIncidentsByElementInstance} from 'modules/queries/incidents/useGetIncidentsByElementInstance';
import {MultiIncidents} from './multiIncidents';
import {SingleIncident} from './singleIncident';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';
import {Divider} from '../styled';

type Props = {
  elementInstanceKey: string;
  elementName: string;
  elementId: string;
};

const Incidents: React.FC<Props> = ({elementInstanceKey, elementName}) => {
  const {data, isLoading: isSearchingIncidents} =
    useGetIncidentsByElementInstance(elementInstanceKey, {
      select: (data) => ({
        totalIncidents: data.page.totalItems,
        singleIncident: data.items.at(0) ?? null,
      }),
    });
  const totalIncidents = data?.totalIncidents ?? 0;
  const singleIncident = data?.singleIncident;

  if (isSearchingIncidents) {
    return (
      <Loading small withOverlay={false} data-testid="incidents-loading" />
    );
  }

  if (totalIncidents === 1 && singleIncident) {
    return (
      <>
        <Divider />
        <SingleIncident
          incident={singleIncident}
          onButtonClick={() => {
            incidentsPanelStore.showIncidentsForElementInstance(
              elementInstanceKey,
              elementName,
            );
          }}
        />
      </>
    );
  }

  if (totalIncidents > 1) {
    return (
      <>
        <Divider />
        <MultiIncidents
          count={totalIncidents}
          onButtonClick={() => {
            incidentsPanelStore.showIncidentsForElementInstance(
              elementInstanceKey,
              elementName,
            );
          }}
        />
      </>
    );
  }

  return null;
};

export {Incidents};
