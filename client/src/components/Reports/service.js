/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, del, put, post} from 'request';

export async function loadSingleReport(id) {
  const response = await get('api/report/' + id);

  return await response.json();
}

export async function remove(id) {
  return await del(`api/report/${id}?force=true`);
}

export async function isSharingEnabled() {
  const response = await get(`api/share/isEnabled`);
  const json = await response.json();
  return json.enabled;
}

export async function evaluateReport(query) {
  let response;

  try {
    if (typeof query !== 'object') {
      // evaluate saved report
      response = await get(`api/report/${query}/evaluate`);
    } else {
      // evaluate unsaved report
      response = await post(`api/report/evaluate/`, query);
    }
  } catch (e) {
    return null;
  }

  return await response.json();
}

export async function saveReport(id, data, forceUpdate) {
  return await put(`api/report/${id}?force=${forceUpdate}`, data);
}

export async function shareReport(reportId) {
  const body = {
    reportId
  };
  const response = await post(`api/share/report`, body);

  const json = await response.json();
  return json.id;
}

export async function getSharedReport(reportId) {
  const response = await get(`api/share/report/${reportId}`);

  if (response.status > 201) {
    return '';
  } else {
    const json = await response.json();
    return json.id;
  }
}

export async function revokeReportSharing(id) {
  return await del(`api/share/report/${id}`);
}
