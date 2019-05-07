/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const ALL_VERSIONS_OPTION = 'all';

// values that we read from the url and prefill the inputs
export const DEFAULT_CONTROLLED_VALUES = {
  ids: '',
  errorMessage: '',
  startDate: '',
  endDate: '',
  activityId: '',
  version: '',
  workflow: '',
  variablesQuery: {name: '', value: ''}
};
