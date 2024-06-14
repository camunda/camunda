/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

export function getConfig(data, flowNodeId) {
  const {
    definitions,
    configuration: {heatmapTargetValue},
  } = data;
  const {value, unit} = heatmapTargetValue.values[flowNodeId];

  return {
    processDefinitionKey: definitions[0].key,
    processDefinitionVersions: definitions[0].versions,
    tenantIds: definitions[0].tenantIds,
    includedColumns: ['processInstanceId'],
    filter: [
      ...data.filter,
      {
        type: 'flowNodeDuration',
        data: {
          [flowNodeId]: {
            operator: '>',
            value,
            unit,
          },
        },
        filterLevel: 'instance',
      },
    ],
  };
}
