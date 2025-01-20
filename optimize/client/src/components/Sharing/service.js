/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {get, post} from 'request';
import { getFullURL } from '../../modules/api';

export async function evaluateEntity(id, type, query = {}) {
  const request = type === 'dashboard' ? get : post;
  const response = await request(getFullURL(`api/share/${type}/${id}/evaluate`), {}, {query});

  return await response.json();
}

export function createLoadReportCallback(dashboardShareId) {
  return async (reportId, filter, query) => {
    const response = await post(
      getFullURL(`api/share/dashboard/${dashboardShareId}/report/${reportId}/evaluate`),
      {filter},
      {query}
    );

    return await response.json();
  };
}
