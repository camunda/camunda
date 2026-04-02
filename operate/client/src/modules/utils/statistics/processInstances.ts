/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessDefinitionStatistic} from '@camunda/camunda-api-zod-schemas/8.10';

function getInstancesCount(
  data: ProcessDefinitionStatistic[],
  elementId?: string,
) {
  const elementStatistics = data.find(
    (statistics) => statistics.elementId === elementId,
  );

  if (elementStatistics === undefined) {
    return 0;
  }

  return elementStatistics.active + elementStatistics.incidents;
}

export {getInstancesCount};
