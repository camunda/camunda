/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import listIncidentsAsTable from './images/listIncidentsAsTable.png';

export function listIncidentsAsTableTemplate() {
  return {
    name: 'listIncidentsAsTable',
    img: listIncidentsAsTable,
    disabled: (definitions: unknown[]) => definitions.length === 0,
    config: {
      configuration: {
        showInstanceCount: true,
        tableColumns: {
          includeNewVariables: true,
          excludedColumns: [],
          includedColumns: [],
          columnOrder: ['Incidents by Flow Node', 'Count', 'Relative Frequency '],
        },
      },
      view: {
        entity: 'incident',
        properties: ['frequency'],
      },
      groupBy: {
        type: 'flowNodes',
        value: null,
      },
      visualization: 'table',
    },
  };
}
