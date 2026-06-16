/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getStage} from './getStage';

const MODELER_URL_BY_STAGE = {
  dev: 'https://modeler.cloud.dev.ultrawombat.com',
  int: 'https://modeler.cloud.ultrawombat.com',
  prod: 'https://modeler.cloud.camunda.io',
} as const;

function getSaasModelerUrl(): string | null {
  if (typeof window === 'undefined') return null;
  const stage = getStage(window.location.host);
  return (
    MODELER_URL_BY_STAGE[stage as keyof typeof MODELER_URL_BY_STAGE] ?? null
  );
}

export {getSaasModelerUrl};
