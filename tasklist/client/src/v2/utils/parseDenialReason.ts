/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';
import {type ProblemDetailsResponse} from '@vzeta/camunda-api-zod-schemas';

export const parseDenialReason = async (
  errorPayload: ProblemDetailsResponse,
  type: 'assignment' | 'unassignment' | 'completion',
): Promise<string | undefined> => {
  if (!errorPayload || errorPayload.status !== 409) return undefined;

  const detail =
    typeof errorPayload?.detail === 'string' ? errorPayload?.detail : '';

  let deniedReason: string = t('deniedReason', {type});
  const match = detail.match(/Reason to deny:\s*(.*)/i);
  if (match && match[1]) {
    deniedReason = match[1].trim().replace(/^["'](.*)["']$/, '$1');
  }

  return deniedReason;
};
