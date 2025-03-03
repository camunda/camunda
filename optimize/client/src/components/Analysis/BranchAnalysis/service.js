/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {post} from 'request';

export async function loadFrequencyData(
  processDefinitionKey,
  processDefinitionVersions,
  tenantIds,
  identifier,
  filter
) {
  const response = await post(
    'api/report/evaluate',
    createFlowNodeFrequencyReport(
      processDefinitionKey,
      processDefinitionVersions,
      tenantIds,
      identifier,
      filter
    )
  );

  return await response.json();
}

function createFlowNodeFrequencyReport(key, versions, tenantIds, identifier, filter) {
  return {
    data: {
      definitions: [{key, versions, tenantIds, identifier}],
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
