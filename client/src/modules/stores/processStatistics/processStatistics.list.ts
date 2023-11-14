/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getProcessInstancesRequestFilters} from 'modules/utils/filter';
import {processInstancesStore} from '../processInstances';
import {ProcessStatistics as ProcessStatisticsBase} from './processStatistics.base';
import {action, makeObservable} from 'mobx';

class ProcessStatistics extends ProcessStatisticsBase {
  constructor() {
    super();
    makeObservable(this, {
      startFetching: action,
    });
  }

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
