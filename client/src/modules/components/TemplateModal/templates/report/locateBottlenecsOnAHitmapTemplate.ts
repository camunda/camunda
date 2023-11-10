/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import locateBottlenecsOnAHitmap from './images/locateBottlenecsOnAHitmap.png';

export function locateBottlenecsOnAHitmapTemplate() {
  return {
    name: 'locateBottlenecsOnAHitmap',
    img: locateBottlenecsOnAHitmap,
    disabled: (definitions: unknown[]) => definitions.length === 0,
    config: {
      view: {
        entity: 'flowNode',
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
