/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useFlownodeInstancesStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics';

const useElementInstancesCount = (elementId?: string) => {
  const {data: elementInstanceStatistics} = useFlownodeInstancesStatistics(
    (data) => data.items.find((item) => item.elementId === elementId),
  );

  if (!elementInstanceStatistics) {
    return null;
  }

  return (
    elementInstanceStatistics.active +
    elementInstanceStatistics.completed +
    elementInstanceStatistics.canceled +
    elementInstanceStatistics.incidents
  );
};

export {useElementInstancesCount};
