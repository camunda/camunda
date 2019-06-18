/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const INCIDENTS_BY_ERROR = 'incidentsByError';
export const INSTANCES_BY_WORKFLOW = 'instancesByWorkflow';

export const MESSAGES = {
  [INCIDENTS_BY_ERROR]: {
    noData: 'There are no Instances with Incident.',
    error: 'Incidents by Error Message could not be fetched.'
  },
  [INSTANCES_BY_WORKFLOW]: {
    noData: 'There are no Workflows.',
    error: 'Instances by Workflow could not be fetched.'
  }
};
