/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';
import {problemDetailResponseSchema} from '@camunda/camunda-api-zod-schemas/8.9';

const parseDenialReason = (
  errorPayload: unknown,
  type: 'assignment' | 'unassignment' | 'completion',
): string | undefined => {
  const {data: errorBody, success} =
    problemDetailResponseSchema.safeParse(errorPayload);

  if (!success || errorBody.status !== 409) {
    return undefined;
  }
  const match = errorBody.detail.match(/Reason to deny:\s*(.*)/i);

  if (Array.isArray(match) && match[1]) {
    return match[1].trim().replace(/^["'](.*)["']$/, '$1');
  }

  return t('deniedReason', {type});
};

export {parseDenialReason};
