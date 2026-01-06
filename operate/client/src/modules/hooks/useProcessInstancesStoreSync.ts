/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {processInstancesStore} from 'modules/stores/processInstances';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {buildV2ProcessInstanceData} from 'modules/utils/processInstance/processInstanceDataBuilder';

const useProcessInstancesStoreSync = (
  processInstances: ProcessInstance[],
  totalCount: number,
  isSuccess: boolean,
) => {
  useEffect(() => {
    if (isSuccess) {
      const adaptedInstances = processInstances.map(buildV2ProcessInstanceData);

      processInstancesStore.setProcessInstances({
        processInstances: adaptedInstances,
        filteredProcessInstancesCount: totalCount,
      });
    }
  }, [processInstances, totalCount, isSuccess]);
};

export {useProcessInstancesStoreSync};
