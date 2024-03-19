/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {SingleProcessReportData} from 'types';

export interface KpiTemplate {
  name: string;
  description: string;
  config: Partial<SingleProcessReportData>;
}
