/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

function getCompleteTaskErrorMessage(code: string) {
  if (code === 'Task is not assigned') {
    return 'Task is not claimed';
  }

  if (code.includes('Task is not assigned to')) {
    return 'Task claimed by another user';
  }

  if (code === 'Task is not active') {
    return undefined;
  }

  return 'Service is not reachable';
}

export {getCompleteTaskErrorMessage};
