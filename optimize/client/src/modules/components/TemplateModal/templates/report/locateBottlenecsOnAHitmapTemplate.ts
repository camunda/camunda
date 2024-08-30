/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
