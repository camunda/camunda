/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getProcessInstancesRequestFilters} from 'modules/utils/filter';
import {processInstancesStore} from '../processInstances';
import {ProcessStatistics as ProcessStatisticsBase} from './processStatistics.base';

class ProcessStatistics extends ProcessStatisticsBase {
  init = () => {
    processInstancesStore.addCompletedOperationsHandler(() => {
      const filters = getProcessInstancesRequestFilters();
      const processIds = filters?.processIds ?? [];
      if (processIds.length > 0) {
        this.fetchProcessStatistics();
      }
    });
  };
}

const processStatisticsStore = new ProcessStatistics();

export {processStatisticsStore};
