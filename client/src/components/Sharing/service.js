/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get} from 'request';

export async function evaluateEntity(id, type) {
  let response;

  try {
    response = await get(`api/share/${type}/${id}/evaluate`);
  } catch (e) {
    return null;
  }

  return await response.json();
}

export function createLoadReportCallback(dashboardShareId) {
  return async report => {
    try {
      const response = await get(
        `api/share/dashboard/${dashboardShareId}/report/${report.id}/evaluate`
      );
      return await response.json();
    } catch (error) {
      return await error.json();
    }
  };
}
