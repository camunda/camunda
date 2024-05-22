/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import localeIncidentHotspotsOnAHeatmap from './images/localeIncidentHotspotsOnAHeatmap.png';

export function localeIncidentHotspotsOnAHeatmapTemplate() {
  return {
    name: 'localeIncidentHotspotsOnAHeatmap',
    img: localeIncidentHotspotsOnAHeatmap,
    disabled: (definitions: unknown[]) => definitions.length === 0,
    config: {
      view: {
        entity: 'incident',
        properties: ['duration'],
      },
      groupBy: {
        type: 'flowNodes',
        value: null,
      },
      visualization: 'heat',
    },
  };
}
