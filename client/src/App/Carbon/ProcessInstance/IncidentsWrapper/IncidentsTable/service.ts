/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Incident} from 'modules/stores/incidents';
import {compareAsc} from 'date-fns';

const SECONDARY_SORT_KEY = 'id';

const compareBySecondaryKey = (incidentA: Incident, incidentB: Incident) => {
  return incidentA[SECONDARY_SORT_KEY] > incidentB[SECONDARY_SORT_KEY] ? 1 : -1;
};

const compareByFlowNodeName = (incidentA: Incident, incidentB: Incident) => {
  return incidentA.flowNodeName.toLowerCase() >
    incidentB.flowNodeName.toLowerCase()
    ? 1
    : -1;
};

const compareByJobId = (incidentA: Incident, incidentB: Incident) => {
  if (incidentA.jobId === null) {
    return 1;
  } else if (incidentB.jobId === null) {
    return -1;
  }
  return incidentA.jobId > incidentB.jobId ? 1 : -1;
};

const compareByErrorType = (incidentA: Incident, incidentB: Incident) => {
  if (incidentA.errorType.name === incidentB.errorType.name) {
    return compareBySecondaryKey(incidentA, incidentB);
  } else {
    return incidentA.errorType.name.toLowerCase() >
      incidentB.errorType.name.toLowerCase()
      ? 1
      : -1;
  }
};

const compareByCreationTime = (incidentA: Incident, incidentB: Incident) => {
  return compareAsc(
    new Date(incidentA.creationTime),
    new Date(incidentB.creationTime)
  );
};

function sortIncidents(incidents: Incident[], key: string, order: SortOrder) {
  const incidentsCopy = Array.from(incidents);

  if (key === 'errorType') {
    incidentsCopy.sort(compareByErrorType);
  } else if (key === 'creationTime') {
    incidentsCopy.sort(compareByCreationTime);
  } else if (key === 'jobId') {
    incidentsCopy.sort(compareByJobId);
  } else if (key === 'flowNodeName') {
    incidentsCopy.sort(compareByFlowNodeName);
  }

  if (order === 'desc') {
    return incidentsCopy.reverse();
  }

  return incidentsCopy;
}

export {sortIncidents};
