/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

function getStage(host: string): 'dev' | 'int' | 'prod' | 'unknown' {
  if (host.includes('dev.ultrawombat.com')) {
    return 'dev';
  }

  if (host.includes('ultrawombat.com')) {
    return 'int';
  }

  if (host.includes('camunda.io')) {
    return 'prod';
  }

  return 'unknown';
}

export {getStage};
