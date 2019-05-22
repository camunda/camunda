/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post} from 'request';

export async function loadFrequencyData(
  processDefinitionKey,
  processDefinitionVersion,
  tenantIds,
  filter
) {
  const response = await post(
    'api/report/evaluate',
    createFlowNodeFrequencyReport(processDefinitionKey, processDefinitionVersion, tenantIds, filter)
  );

  return await response.json();
}

function createFlowNodeFrequencyReport(
  processDefinitionKey,
  processDefinitionVersion,
  tenantIds,
  filter
) {
  return {
    combined: false,
    reportType: 'process',
    data: {
      processDefinitionKey,
      processDefinitionVersion,
      tenantIds,
      filter,
      view: {
        entity: 'flowNode',
        property: 'frequency'
      },
      groupBy: {
        type: 'flowNodes',
        unit: null
      },
      visualization: 'heat'
    }
  };
}

export async function loadCorrelationData(
  processDefinitionKey,
  processDefinitionVersion,
  tenantIds,
  filter,
  gateway,
  end
) {
  const response = await post('api/analysis/correlation', {
    processDefinitionKey,
    processDefinitionVersion,
    tenantIds,
    filter,
    gateway,
    end
  });

  return await response.json();
}
