/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Incident} from '@camunda/camunda-api-zod-schemas/8.8';
import {compareAsc} from 'date-fns';
import type {EnhancedIncident} from 'modules/hooks/incidents';
import type {SortOrder} from 'modules/types/operate';

const SECONDARY_SORT_KEY = 'incidentKey';

const compareBySecondaryKey = (incidentA: Incident, incidentB: Incident) => {
  return incidentA[SECONDARY_SORT_KEY] > incidentB[SECONDARY_SORT_KEY] ? 1 : -1;
};

const compareByElementName = (
  incidentA: EnhancedIncident,
  incidentB: EnhancedIncident,
) => {
  return incidentA.elementName.toLowerCase() >
    incidentB.elementName.toLowerCase()
    ? 1
    : -1;
};

const compareByJobId = (incidentA: Incident, incidentB: Incident) => {
  if (incidentA.jobKey === undefined) {
    return 1;
  } else if (incidentB.jobKey === undefined) {
    return -1;
  }
  return incidentA.jobKey > incidentB.jobKey ? 1 : -1;
};

const compareByErrorType = (incidentA: Incident, incidentB: Incident) => {
  if (incidentA.errorType === incidentB.errorType) {
    return compareBySecondaryKey(incidentA, incidentB);
  } else {
    return incidentA.errorType.toLowerCase() > incidentB.errorType.toLowerCase()
      ? 1
      : -1;
  }
};

const compareByCreationTime = (incidentA: Incident, incidentB: Incident) => {
  return compareAsc(
    new Date(incidentA.creationTime),
    new Date(incidentB.creationTime),
  );
};

function sortIncidents(
  incidents: EnhancedIncident[],
  key: string,
  order: SortOrder,
) {
  const incidentsCopy = Array.from(incidents);

  if (key === 'errorType') {
    incidentsCopy.sort(compareByErrorType);
  } else if (key === 'creationTime') {
    incidentsCopy.sort(compareByCreationTime);
  } else if (key === 'jobId') {
    incidentsCopy.sort(compareByJobId);
  } else if (key === 'elementName') {
    incidentsCopy.sort(compareByElementName);
  }

  if (order === 'desc') {
    return incidentsCopy.reverse();
  }

  return incidentsCopy;
}

export {sortIncidents};
