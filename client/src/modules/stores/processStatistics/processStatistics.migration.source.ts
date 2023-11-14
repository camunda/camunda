/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeObservable, override} from 'mobx';
import {ProcessStatistics as ProcessStatisticsBase} from './processStatistics.base';

class ProcessStatistics extends ProcessStatisticsBase {
  constructor() {
    super();
    makeObservable(this, {
      statistics: override,
    });
  }

  get statistics() {
    return this.state.statistics.filter((statistic) => {
      return statistic.active || statistic.incidents;
    });
  }
}

const processStatisticsStore = new ProcessStatistics();

export {processStatisticsStore};
