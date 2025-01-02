/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';

function getCompleteTaskErrorMessage(code: string) {
  if (code === 'Task is not assigned') {
    return t('taskNotCompletedNotAssigned');
  }

  if (code?.includes('Task is not assigned to')) {
    return t('taskNotCompletedAssignedToOther');
  }

  if (code === 'Task is not active') {
    return undefined;
  }

  return t('taskNotCompletedNotReachable');
}

export {getCompleteTaskErrorMessage};
