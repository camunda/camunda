/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {post} from 'request';

interface ConfigParams {
  processDefinitionKey: string;
  processDefinitionVersions: string[];
  tenantIds: string[];
  filter: any[];
  includedColumns: string[];
}

export const TEXT_REPORT_MAX_CHARACTERS = 3000;

export function isTextReportValid(textLength: number): boolean {
  return textLength > 0 && !isTextReportTooLong(textLength);
}

export function isTextReportTooLong(
  textLength: number,
  limit: number = TEXT_REPORT_MAX_CHARACTERS
): boolean {
  return textLength > limit;
}

export async function loadRawData(config: ConfigParams): Promise<Blob> {
  const response = await post('api/export/csv/process/rawData/data', config);

  return await response.blob();
}
