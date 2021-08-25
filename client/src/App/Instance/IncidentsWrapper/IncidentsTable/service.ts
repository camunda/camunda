/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {SORT_ORDER} from 'modules/constants';
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

// TODO: remove, when IS_NEXT_INCIDENTS is removed
function sanitize(value: string | number) {
  return typeof value === 'string' ? value.toLowerCase() : value;
}

// TODO: remove, when IS_NEXT_INCIDENTS is removed
function sortData(data: any, key: any, order: any) {
  const modifier = order === SORT_ORDER.DESC ? -1 : 1;

  function compare(a: any, b: any) {
    // we want empty values to come last
    if (!a[key]) return 1;
    if (!b[key]) return -1;

    const valA = sanitize(a[key]);
    const valB = sanitize(b[key]);

    const comparison =
      key === 'creationTime'
        ? new Date(a[key]) > new Date(b[key])
        : valA > valB;

    // this will sort entries with same value by secondary sort key
    if (valA === valB)
      return a[SECONDARY_SORT_KEY] > b[SECONDARY_SORT_KEY] ? 1 : -1;

    return (comparison ? 1 : -1) * modifier;
  }

  let arr = data.slice(0);
  arr.sort(compare);

  return arr;
}

export {sortData, sortIncidents};
