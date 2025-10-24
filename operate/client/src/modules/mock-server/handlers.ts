/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  Incident,
  QueryIncidentsRequestBody,
  QueryIncidentsResponseBody,
  QuerySortOrder,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {compareAsc} from 'date-fns';
import {IS_INCIDENTS_PANEL_V2} from 'modules/feature-flags';
import {createIncidentV2} from 'modules/testUtils';
import {availableErrorTypes} from 'modules/utils/incidents';
import {
  bypass,
  http,
  HttpResponse,
  RequestHandler,
  type StrictRequest,
} from 'msw';

const handlers: RequestHandler[] = [];

if (IS_INCIDENTS_PANEL_V2) {
  handlers.push(
    http.post<
      {elementInstanceKey: string},
      QueryIncidentsRequestBody,
      QueryIncidentsResponseBody
    >(
      '/v2/element-instances/:elementInstanceKey/incidents/search',
      handleRequest.bind(this, 'ele'),
    ),
    http.post<
      {processInstanceKey: string},
      QueryIncidentsRequestBody,
      QueryIncidentsResponseBody
    >(
      '/v2/process-instances/:processInstanceKey/incidents/search',
      handleRequest.bind(this, 'proc'),
    ),
  );
}

export {handlers};

// TODO: Remove/Simplify all code below for testing incident pagination once
// https://github.com/camunda/camunda/issues/39638 and https://github.com/camunda/camunda/issues/39637 are implemented.
async function handleRequest(
  identifier: string,
  {request}: {request: StrictRequest<QueryIncidentsRequestBody>},
): Promise<HttpResponse<QueryIncidentsResponseBody>> {
  const bypassReq = new Request(request.clone(), {body: ''});
  const response = await fetch(bypass(bypassReq));
  const result = (await response.json()) as QueryIncidentsResponseBody;
  const query = await request.json();

  const fakeData = Array.from({length: 800}).map((_, index) =>
    createIncidentV2({
      ...result.items[index % result.items.length],
      incidentKey: `incident-${index}`,
      jobKey: `job-${identifier}-${index}`,
      errorType: availableErrorTypes.slice(0, 5)[index % 5] ?? 'UNSPECIFIED',
    }),
  );

  const filter = query.filter;
  const filtered = fakeData.filter((incident) => {
    if (filter?.state && incident.state !== filter.state) {
      return false;
    }
    if (
      typeof filter?.errorType === 'object' &&
      !filter.errorType.$in?.includes(incident.errorType)
    ) {
      return false;
    }

    return true;
  });

  const sort = query.sort?.at(0);
  sortIncidents(filtered, sort?.field ?? 'creationTime', sort?.order ?? 'desc');

  const start = query.page?.from ?? 0;
  const end = start + (query.page?.limit ?? 100);
  console.log('Incidents-Search result in range:', {start, end});
  return HttpResponse.json({
    items: filtered.slice(start, end),
    page: {
      totalItems: filtered.length,
    },
  });
}

function sortIncidents(
  incidents: Incident[],
  key: keyof Incident,
  order: QuerySortOrder,
) {
  if (key === 'errorType') {
    incidents.sort(compareByErrorType);
  } else if (key === 'creationTime') {
    incidents.sort(compareByCreationTime);
  } else if (key === 'jobKey') {
    incidents.sort(compareByJobKey);
  }

  if (order === 'desc') {
    return incidents.reverse();
  }

  return incidents;
}

const compareBySecondaryKey = (incidentA: Incident, incidentB: Incident) => {
  return incidentA['incidentKey'] > incidentB['incidentKey'] ? 1 : -1;
};

const compareByJobKey = (incidentA: Incident, incidentB: Incident) => {
  if (incidentA.jobKey === undefined) {
    return 1;
  } else if (incidentB.jobKey === undefined) {
    return -1;
  }
  return incidentA.jobKey.localeCompare(incidentB.jobKey);
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
