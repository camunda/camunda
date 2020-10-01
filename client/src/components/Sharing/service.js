/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, post} from 'request';

export async function evaluateEntity(id, type, params = {}) {
  let response;

  const request = type === 'dashboard' ? get : post;
  try {
    response = await request(`api/share/${type}/${id}/evaluate`, params);
  } catch (e) {
    return (await e.json()).reportDefinition;
  }

  return await response.json();
}

export function createLoadReportCallback(dashboardShareId) {
  return async (reportId, filter) => {
    try {
      const response = await post(
        `api/share/dashboard/${dashboardShareId}/report/${reportId}/evaluate`,
        {filter}
      );
      return await response.json();
    } catch (error) {
      return (await error.json()).reportDefinition;
    }
  };
}
