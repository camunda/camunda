/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

function getTaskAssignmentChangeErrorMessage(code: string) {
  if (code === 'Task is already assigned') {
    return undefined;
  }

  if (code === 'Task is not active') {
    return undefined;
  }

  if (code === 'Task is not assigned') {
    return undefined;
  }

  return 'Service is not reachable';
}

export {getTaskAssignmentChangeErrorMessage};
