/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get, post} from 'request';

export async function evaluateEntity(id, type, query = {}) {
  const request = type === 'dashboard' ? get : post;
  const response = await request(`api/share/${type}/${id}/evaluate`, {}, {query});

  return await response.json();
}

export function createLoadReportCallback(dashboardShareId) {
  return async (reportId, filter, query) => {
    const response = await post(
      `api/share/dashboard/${dashboardShareId}/report/${reportId}/evaluate`,
      {filter},
      {query}
    );

    return await response.json();
  };
}
