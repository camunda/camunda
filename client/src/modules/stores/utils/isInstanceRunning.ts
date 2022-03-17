/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const isInstanceRunning = (instance: ProcessInstanceEntity | null) => {
  return instance?.state === 'ACTIVE' || instance?.state === 'INCIDENT';
};

export {isInstanceRunning};
