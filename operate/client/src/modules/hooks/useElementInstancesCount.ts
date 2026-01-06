/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useFlownodeInstancesStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics';

const useElementInstancesCount = (elementId?: string) => {
  const {data: statistics} = useFlownodeInstancesStatistics();

  if (!statistics?.items || !elementId) {
    return null;
  }
  const elementStats = statistics.items.find(
    (stat) => stat.elementId === elementId,
  );
  if (!elementStats) {
    return null;
  }

  return (
    elementStats.active +
    elementStats.completed +
    elementStats.canceled +
    elementStats.incidents
  );
};

export {useElementInstancesCount};
