/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {LineChartOptions, BarChartOptions} from '@carbon/charts-react';

export type VisualizationType = 'timeline' | 'bar' | 'table' | 'none';

export type TimeSeriesDataPoint = {
  group: string;
  date: Date;
  value: number;
};

export type CategoryDataPoint = {
  group: string;
  value: number;
};

export type VisualizationData = {
  type: VisualizationType;
  title?: string;
  data: TimeSeriesDataPoint[] | CategoryDataPoint[];
  options?: Partial<LineChartOptions> | Partial<BarChartOptions>;
};

export type ProcessInstanceItem = {
  processInstanceKey?: string;
  startDate?: string;
  endDate?: string;
  state?: string;
  [key: string]: unknown;
};

export type IncidentItem = {
  incidentKey?: string;
  creationTime?: string;
  errorType?: string;
  errorMessage?: string;
  [key: string]: unknown;
};
