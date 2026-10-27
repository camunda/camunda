/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import analyzeOrExportRawDataFromATable from './images/analyzeOrExportRawDataFromATable.png';

export function analyzeOrExportRawDataFromATableTemplate() {
  return {
    name: 'analyzeOrExportRawDataFromATable',
    img: analyzeOrExportRawDataFromATable,
    disabled: (definitions: unknown[]) => definitions.length === 0,
    config: {
      configuration: {
        showInstanceCount: true,
        sorting: {
          by: 'startDate',
          order: 'desc',
        },
      },
      view: {
        entity: null,
        properties: ['rawData'],
      },
      groupBy: {
        type: 'none',
        value: null,
      },
      visualization: 'table',
    },
  };
}
