/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';

function getTaskAssignmentChangeErrorMessage(
  code?: string,
  statusCode?: number,
  isAssigning?: boolean,
) {
  if (code === 'Task is already assigned') {
    return 'Task has been assigned to another user';
  }

  if (code === 'Task is not active') {
    return undefined;
  }

  if (code === 'Task is not assigned') {
    return undefined;
  }

  if (statusCode === 409) {
    return isAssigning
      ? t('taskDetailsTaskUnassignmentRejectionErrorSubtitle')
      : t('taskDetailsTaskAssignmentRejectionErrorSubtitle');
  }

  return 'Service is not reachable';
}

export {getTaskAssignmentChangeErrorMessage};
