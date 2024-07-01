/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
