/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type QueryProcessInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {request, type RequestError} from 'modules/request';

const EXPORT_URL = '/v2/process-instances/search.csv';
const TRUNCATED_HEADER = 'x-camunda-export-truncated';

type ExportResult = {
  blob: Blob;
  truncated: boolean;
};

const exportProcessInstancesCsv = async (
  payload: QueryProcessInstancesRequestBody,
): Promise<
  {response: ExportResult; error: null} | {response: null; error: RequestError}
> => {
  try {
    const response = await request({
      url: EXPORT_URL,
      method: 'POST',
      body: payload,
      headers: {Accept: 'text/csv'},
    });

    if (!response.ok) {
      return {
        response: null,
        error: {response, networkError: null, variant: 'failed-response'},
      };
    }

    return {
      response: {
        blob: await response.blob(),
        truncated: response.headers.get(TRUNCATED_HEADER) === 'true',
      },
      error: null,
    };
  } catch (networkError) {
    return {
      response: null,
      error: {response: null, networkError, variant: 'network-error'},
    };
  }
};

export {exportProcessInstancesCsv};
export type {ExportResult};
