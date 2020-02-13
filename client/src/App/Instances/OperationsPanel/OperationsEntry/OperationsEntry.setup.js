/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {OPERATION_TYPES} from './constants';

export const OPERATIONS = {
  RETRY: {
    id: 'b42fd629-73b1-4709-befb-7ccd900fb18d',
    type: OPERATION_TYPES.RESOLVE_INCIDENT,
    endDate: null
  },
  CANCEL: {
    id: '393ad666-d7f0-45c9-a679-ffa0ef82f88a',
    type: OPERATION_TYPES.CANCEL_WORKFLOW_INSTANCE,
    endDate: '2020-02-06T14:56:17.932+0100'
  },
  EDIT: {
    id: 'df325d44-6a4c-4428-b017-24f923f1d052',
    type: OPERATION_TYPES.UPDATE_VARIABLE,
    endDate: '2020-02-06T14:56:17.932+0100'
  }
};
