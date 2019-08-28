/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, formatQuery} from 'request';

export async function loadNodesOutliers(config) {
  const response = await get('api/analysis/flowNodeOutliers', config);
  return await response.json();
}

export async function loadDurationData(params) {
  const response = await get('api/analysis/durationChart', params);
  return await response.json();
}

export async function loadCommonOutliersVariables(params) {
  const response = await get('api/analysis/significantOutlierVariableTerms', params);
  return await response.json();
}

export function getInstancesDownloadUrl(query) {
  return `api/analysis/significantOutlierVariableTerms/processInstanceIdsExport?${formatQuery(
    query
  )}`;
}
