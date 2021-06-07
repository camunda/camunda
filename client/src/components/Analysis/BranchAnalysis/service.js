/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post} from 'request';

export async function loadFrequencyData(
  processDefinitionKey,
  processDefinitionVersions,
  tenantIds,
  filter
) {
  const response = await post(
    'api/report/evaluate',
    createFlowNodeFrequencyReport(
      processDefinitionKey,
      processDefinitionVersions,
      tenantIds,
      filter
    )
  );

  return await response.json();
}

function createFlowNodeFrequencyReport(key, versions, tenantIds, filter) {
  return {
    combined: false,
    reportType: 'process',
    data: {
      definitions: [{key, versions, tenantIds}],
      filter,
      view: {
        entity: 'flowNode',
        properties: ['frequency'],
      },
      groupBy: {
        type: 'flowNodes',
        unit: null,
      },
      visualization: 'heat',
    },
  };
}

export async function loadCorrelationData(
  processDefinitionKey,
  processDefinitionVersions,
  tenantIds,
  filter,
  gateway,
  end
) {
  const response = await post('api/analysis/correlation', {
    processDefinitionKey,
    processDefinitionVersions,
    tenantIds,
    filter,
    gateway,
    end,
  });

  return await response.json();
}
