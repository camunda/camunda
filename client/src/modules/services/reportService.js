/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, post} from 'request';

export function isDurationReport(report) {
  // waiting for optional chaining... https://github.com/tc39/proposal-optional-chaining
  return (
    report &&
    report.data &&
    report.data.view &&
    report.data.view.property &&
    report.data.view.property.toLowerCase().includes('duration')
  );
}

export async function evaluateReport(query) {
  let response;

  if (typeof query !== 'object') {
    // evaluate saved report
    response = await get(`api/report/${query}/evaluate`);
  } else {
    // evaluate unsaved report
    response = await post(`api/report/evaluate/`, query);
  }

  return await response.json();
}
