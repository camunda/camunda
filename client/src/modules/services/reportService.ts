/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {post} from 'request';

import {Report, ReportType} from 'types';

interface ConfigParams {
  processDefinitionKey: string;
  processDefinitionVersions: string[];
  tenantIds: string[];
  filter: any[];
  includedColumns: string[];
}

export const TEXT_REPORT_MAX_CHARACTERS = 3000;

export function isTextTileValid(textLength: number): boolean {
  return textLength > 0 && !isTextTileTooLong(textLength);
}

export function isTextTileTooLong(
  textLength: number,
  limit: number = TEXT_REPORT_MAX_CHARACTERS
): boolean {
  return textLength > limit;
}

export async function loadRawData(config: ConfigParams): Promise<Blob> {
  const response = await post('api/export/csv/process/rawData/data', config);

  return await response.blob();
}

type DeepPartial<T> = T extends object
  ? {
      [P in keyof T]?: DeepPartial<T[P]>;
    }
  : T;

export type ReportPayload<T extends ReportType> = DeepPartial<Report<T>>;

export async function evaluateReport<T extends ReportType>(
  payload: ReportPayload<T>,
  filter = [],
  query = {}
): Promise<Report<T>> {
  let response;

  if (typeof payload !== 'object') {
    // evaluate saved report
    response = await post(`api/report/${payload}/evaluate`, {filter}, {query});
  } else {
    // evaluate unsaved report
    // we dont want to send report result in payload to prevent exceedeing request size limit
    const {result, ...evaluationPayload} = payload;
    response = await post(`api/report/evaluate/`, evaluationPayload, {query});
  }

  return await response.json();
}
