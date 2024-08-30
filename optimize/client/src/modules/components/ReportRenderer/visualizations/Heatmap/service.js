/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
