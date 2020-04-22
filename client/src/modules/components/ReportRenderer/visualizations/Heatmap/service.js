/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {formatters} from 'services';
const {convertToMilliseconds} = formatters;

export function calculateTargetValueHeat(durationData, targetValues) {
  const data = {};

  Object.keys(targetValues).forEach((element) => {
    const targetValueInMs = convertToMilliseconds(
      targetValues[element].value,
      targetValues[element].unit
    );

    if (durationData[element] > targetValueInMs) {
      data[element] = durationData[element] / targetValueInMs - 1;
    } else {
      data[element] = null;
    }
  });

  return data;
}

export function createFlowNodeReport(data, flowNodeId) {
  const {
    configuration: {heatmapTargetValue},
  } = data;
  const {value, unit} = heatmapTargetValue.values[flowNodeId];

  return {
    combined: false,
    reportType: 'process',
    data: {
      ...data,
      filter: [
        ...data.filter,
        {
          type: 'flowNodeDuration',
          data: {
            flowNodeId,
            operator: '>',
            value,
            unit,
          },
        },
      ],
      view: {
        entity: null,
        property: 'rawData',
      },
      groupBy: {
        type: 'none',
        value: null,
      },
      visualization: 'table',
    },
  };
}
