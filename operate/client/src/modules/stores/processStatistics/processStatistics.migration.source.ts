/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
