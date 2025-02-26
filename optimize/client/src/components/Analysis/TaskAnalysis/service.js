/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {post} from 'request';

export async function loadNodesOutliers(config) {
  const response = await post('api/analysis/flowNodeOutliers', config);
  return await response.json();
}

export {
  loadDurationData,
  loadCommonOutliersVariables,
  getOutlierSummary,
  shouldUseLogharitmicScale,
} from './service.ts';
