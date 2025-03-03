/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {post} from 'request';
import {Report} from 'types';

interface ConfigParams {
  processDefinitionKey: string;
  processDefinitionVersions: string[];
  tenantIds: string[];
  filter: unknown[];
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

export type ReportEvaluationPayload = DeepPartial<Report>;

export async function evaluateReport(
  payload: ReportEvaluationPayload,
  filter = [],
  query = {}
): Promise<Report> {
  let response;

  if (typeof payload !== 'object') {
    // evaluate saved report
    response = await post(`api/report/${payload}/evaluate`, {filter}, {query});
  } else {
    // evaluate unsaved report
    // we dont want to send report result in payload to prevent exceedeing request size limit
    const {result: _result, ...evaluationPayload} = payload;
    response = await post(
      `api/report/evaluate`,
      {...evaluationPayload, combined: false, reportType: 'process'},
      {query}
    );
  }

  return await response.json();
}
