/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const totalInstancesLink = Selector(
  '[data-test="total-instances-link"]'
);
export const activeInstancesLink = Selector(
  '[data-test="active-instances-link"]'
);
export const incidentInstancesLink = Selector(
  '[data-test="incident-instances-link"]'
);
export const activeInstancesBadge = Selector(
  '[data-test="active-instances-badge"]'
);
export const incidentInstancesBadge = Selector(
  '[data-test="incident-instances-badge"]'
);
