/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {FilterType, ProcessFilter, SingleProcessReportData} from 'types';

export interface DefaultProcessFilter<T extends FilterType = FilterType>
  extends Omit<ProcessFilter<T>, 'appliedTo'> {
  label: string;
  description: string;
}

export interface KpiTemplate {
  name: string;
  description: string;
  img: string;
  config: Omit<Partial<SingleProcessReportData>, 'configuration'> & {
    configuration?: Partial<SingleProcessReportData['configuration']>;
  };
  uiConfig: {
    filters: DefaultProcessFilter[];
  };
}
