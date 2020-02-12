/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, del, post} from 'request';

export async function shareDashboard(dashboardId) {
  const body = {
    dashboardId
  };
  const response = await post(`api/share/dashboard`, body);

  const json = await response.json();
  return json.id;
}

export async function getSharedDashboard(reportId) {
  const response = await get(`api/share/dashboard/${reportId}`);

  if (response.status > 201) {
    return '';
  } else {
    const json = await response.json();
    return json.id;
  }
}

export async function revokeDashboardSharing(id) {
  return await del(`api/share/dashboard/${id}`);
}

export async function isAuthorizedToShareDashboard(dashboardId) {
  try {
    const response = await get(`api/share/dashboard/${dashboardId}/isAuthorizedToShare`);
    return response.status === 200;
  } catch (error) {
    return false;
  }
}
