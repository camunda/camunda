/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const report = text =>
  Selector('.TypeaheadMultipleSelection__valueListItem')
    .withText(text)
    .find('input');
export const chartRenderer = Selector('.ChartRenderer');
