/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

function getCompleteTaskErrorMessage(code: string) {
  if (code === 'Task is not assigned') {
    return 'Task is not assigned';
  }

  if (code.includes('Task is not assigned to')) {
    return 'Task assigned to another user';
  }

  if (code === 'Task is not active') {
    return undefined;
  }

  return 'Service is not reachable';
}

export {getCompleteTaskErrorMessage};
